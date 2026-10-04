package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.KnowledgeVectorClient;
import org.icedamericanomall.dto.KnowledgeVectorCO;
import org.icedamericanomall.dto.KnowledgeVectorSearchCO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 知识库向量服务降级工厂 —— <b>fail-open（返回失败哨兵，不抛异常）</b>。
 *
 * <p>为什么是 fail-open：向量索引是 AI 客服/RAG 的增强能力，<b>属于可失败的旁路</b>。
 * <ul>
 *   <li>摄取/删除失败：知识条目仍在 MySQL 中落库（{@code merchant_knowledge_base}），
 *       只是本次没建立/清理索引 —— 抛异常会连带 CRUD 主流程失败，违反「不阻断知识库 CRUD」约定；</li>
 *   <li>检索失败：等价于「检索无结果」，客服 Agent 直接无 RAG 应答（对话仍然可用）。</li>
 * </ul>
 *
 * <p>哨兵口径：{@code -1} = 未写入任何 chunk（调用方不得据此写入 embeddingId）；
 * {@code false} = 未删除；空列表 = 无命中。
 */
@Slf4j
@Component
public class KnowledgeVectorClientFallback implements FallbackFactory<KnowledgeVectorClient> {

    @Override
    public KnowledgeVectorClient create(Throwable cause) {
        return new KnowledgeVectorClient() {

            @Override
            public Integer upsertKnowledgeVectors(KnowledgeVectorCO co) {
                log.warn("知识库向量摄取降级：本次未建立索引 knowledgeId={}",
                        co == null ? null : co.getKnowledgeId(), cause);
                return -1;
            }

            @Override
            public Boolean deleteKnowledgeVectors(Long knowledgeId) {
                log.warn("知识库向量删除降级：本次未清理索引 knowledgeId={}", knowledgeId, cause);
                return Boolean.FALSE;
            }

            @Override
            public List<Map<String, Object>> vectorSearch(KnowledgeVectorSearchCO co) {
                log.warn("知识库向量检索降级：本次按无命中处理 index={}",
                        co == null ? null : co.getIndex(), cause);
                return List.of();
            }
        };
    }
}
