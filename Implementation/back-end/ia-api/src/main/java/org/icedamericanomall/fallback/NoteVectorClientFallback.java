package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.NoteVectorClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.dto.NoteVectorHitCO;
import org.icedamericanomall.dto.NoteVectorUpsertCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 笔记向量推荐降级工厂 —— <b>fail-open（纯旁路，ADR-046 同口径）</b>。
 *
 * <p>依据（读调用方得出，两个消费点均自带 try/catch）：
 * <ul>
 *   <li>{@code item NoteVectorManager#sendSafely}：摄取降级返回哨兵 {@code -1} 并告警，
 *       笔记主流程零感知（向量可由 reindex 补齐）；</li>
 *   <li>{@code item NoteManager#similarHitIds}：相似检索失败按空列表处理，
 *       相似笔记位可空是接受的产品形态。</li>
 * </ul>
 *
 * <p>哨兵口径：{@code -1} = 本笔记向量未更新（调用方据此告警；与 0/正值区分），
 * 空列表 = 无相似命中。{@code category == CLIENT} 时原样抛 {@link UpstreamException}
 * （调用方 try/catch 兜住，抛出不改主流程，只为让契约违约可观测）。
 */
@Slf4j
@Component
public class NoteVectorClientFallback implements FallbackFactory<NoteVectorClient> {

    /** 摄取降级哨兵（与 I47 哨兵口径一致）。 */
    public static final int UPSERT_FALLBACK_SENTINEL = -1;

    @Override
    public NoteVectorClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        return new NoteVectorClient() {

            @Override
            public int upsert(NoteVectorUpsertCO co) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("笔记向量摄取降级(透传): 下游业务失败 noteId={}, code={}, msg={}",
                            co == null ? null : co.getNoteId(), upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.warn("笔记向量摄取降级：暂时无法获取，本笔记向量未更新（哨兵 -1）noteId={}, category={}",
                        co == null ? null : co.getNoteId(), category, cause);
                return UPSERT_FALLBACK_SENTINEL;
            }

            @Override
            public List<NoteVectorHitCO> similar(Long noteId, int size) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("相似笔记检索降级(透传): 下游业务失败 noteId={}, code={}, msg={}",
                            noteId, upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.warn("相似笔记检索降级：暂时无法获取，相似笔记位返回空 noteId={}, category={}",
                        noteId, category, cause);
                return List.of();
            }
        };
    }
}
