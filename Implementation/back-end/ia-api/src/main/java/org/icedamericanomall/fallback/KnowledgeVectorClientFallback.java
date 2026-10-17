package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.KnowledgeVectorClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.dto.KnowledgeVectorCO;
import org.icedamericanomall.dto.KnowledgeVectorSearchCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 知识库向量服务降级工厂 —— <b>fail-open（返回失败哨兵，不抛异常）</b>。
 *
 * <p>为什么是 fail-open：向量索引是 AI 客服/RAG 的增强能力，<b>属于可失败的旁路</b>。
 * 依据（读调用方得出，三个消费点均自带防御）：
 * <ul>
 *   <li>{@code ai-service FeignKnowledgeVectorAdapter}：摄取/删除失败 → 返回
 *       {@code UPSERT_FAILED} / {@code false}，知识条目本身仍在 MySQL 落库，只是本次没建/清索引；</li>
 *   <li>{@code ai-service FeignVectorSearchAdapter#doSearch}：检索失败 → 按「无命中」返回空列表
 *       （客服照常应答，只是没有 RAG 上下文）。</li>
 * </ul>
 *
 * <p>哨兵口径（<b>让调用方能区分「真没有」与「拿不到」</b>）：
 * {@code -1} = 未写入任何 chunk（调用方不得据此写入 embeddingId）；
 * {@code false} = 未删除；空列表 = 无命中。
 *
 * <p>分支：{@code category == CLIENT} → 原样抛 {@link UpstreamException}
 * （调用方两处均 try/catch，抛出不改主流程，但能让契约违约显式暴露）；
 * {@code TRANSIENT} / {@code BUG} → 返回上述哨兵。
 */
@Slf4j
@Component
public class KnowledgeVectorClientFallback implements FallbackFactory<KnowledgeVectorClient> {

    /** 摄取降级哨兵（契约 §4.14.10 I47 口径：负数 = 未写入任何 chunk）。 */
    public static final int UPSERT_FALLBACK_SENTINEL = -1;

    @Override
    public KnowledgeVectorClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        return new KnowledgeVectorClient() {

            @Override
            public Integer upsertKnowledgeVectors(KnowledgeVectorCO co) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("知识库向量摄取降级(透传): 下游业务失败 knowledgeId={}, code={}, msg={}",
                            co == null ? null : co.getKnowledgeId(),
                            upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.warn("知识库向量摄取降级：暂时无法获取，本次未建立索引（哨兵 -1）knowledgeId={}, category={}",
                        co == null ? null : co.getKnowledgeId(), category, cause);
                return UPSERT_FALLBACK_SENTINEL;
            }

            @Override
            public Boolean deleteKnowledgeVectors(Long knowledgeId) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("知识库向量删除降级(透传): 下游业务失败 knowledgeId={}, code={}, msg={}",
                            knowledgeId, upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.warn("知识库向量删除降级：暂时无法获取，本次未清理索引（false）knowledgeId={}, category={}",
                        knowledgeId, category, cause);
                return Boolean.FALSE;
            }

            @Override
            public List<Map<String, Object>> vectorSearch(KnowledgeVectorSearchCO co) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("知识库向量检索降级(透传): 下游业务失败 index={}, code={}, msg={}",
                            co == null ? null : co.getIndex(), upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.warn("知识库向量检索降级：暂时无法获取，本次按无命中处理 index={}, category={}",
                        co == null ? null : co.getIndex(), category, cause);
                return List.of();
            }
        };
    }
}
