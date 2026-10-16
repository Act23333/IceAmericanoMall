package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.SpuVectorClient;
import org.icedamericanomall.dto.SpuVectorHitCO;
import org.icedamericanomall.dto.SpuVectorUpsertCO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商品向量推荐降级工厂 —— <b>fail-open（纯旁路，ADR-046）</b>。
 *
 * <ul>
 *   <li>摄取降级返回哨兵 {@code -1} 并告警（对齐知识库 KnowledgeVectorClient 哨兵口径）——
 *       商品主流程零感知，向量可由后续编辑或 reindex 全量重建补齐；</li>
 *   <li>相似检索降级返回空列表并告警——推荐位可空是接受的产品形态，商品页不可被推荐拖垮。</li>
 * </ul>
 */
@Slf4j
@Component
public class SpuVectorClientFallback implements FallbackFactory<SpuVectorClient> {

    /** 摄取降级哨兵（与知识库 upsert 哨兵口径一致）。 */
    public static final int UPSERT_FALLBACK_SENTINEL = -1;

    @Override
    public SpuVectorClient create(Throwable cause) {
        return new SpuVectorClient() {

            @Override
            public int upsert(SpuVectorUpsertCO co) {
                log.warn("商品向量摄取降级：search-service 不可用，本商品向量未更新（可由 reindex 补齐）"
                        + " spuId={}", co == null ? null : co.getSpuId(), cause);
                return UPSERT_FALLBACK_SENTINEL;
            }

            @Override
            public List<SpuVectorHitCO> similar(String spuId, int size) {
                log.warn("相似商品检索降级：search-service 不可用，推荐位返回空 spuId={}", spuId, cause);
                return List.of();
            }
        };
    }
}
