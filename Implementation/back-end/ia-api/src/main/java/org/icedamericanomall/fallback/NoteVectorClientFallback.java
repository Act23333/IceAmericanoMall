package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.NoteVectorClient;
import org.icedamericanomall.dto.NoteVectorHitCO;
import org.icedamericanomall.dto.NoteVectorUpsertCO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 笔记向量推荐降级工厂 —— <b>fail-open（纯旁路，ADR-046 同口径）</b>。
 *
 * <ul>
 *   <li>摄取降级返回哨兵 {@code -1} 并告警（对齐 I47 哨兵口径）——笔记主流程零感知；</li>
 *   <li>相似检索降级返回空列表并告警——相似笔记位可空是接受的产品形态。</li>
 * </ul>
 */
@Slf4j
@Component
public class NoteVectorClientFallback implements FallbackFactory<NoteVectorClient> {

    /** 摄取降级哨兵（与 I47 哨兵口径一致）。 */
    public static final int UPSERT_FALLBACK_SENTINEL = -1;

    @Override
    public NoteVectorClient create(Throwable cause) {
        return new NoteVectorClient() {

            @Override
            public int upsert(NoteVectorUpsertCO co) {
                log.warn("笔记向量摄取降级：search-service 不可用，本笔记向量未更新 noteId={}",
                        co == null ? null : co.getNoteId(), cause);
                return UPSERT_FALLBACK_SENTINEL;
            }

            @Override
            public List<NoteVectorHitCO> similar(Long noteId, int size) {
                log.warn("相似笔记检索降级：search-service 不可用，相似笔记位返回空 noteId={}", noteId, cause);
                return List.of();
            }
        };
    }
}
