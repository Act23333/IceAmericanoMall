package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.ProductVectorClient;
import org.icedamericanomall.dto.ProductVectorHitCO;
import org.icedamericanomall.dto.ProductVectorUpsertCO;
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
public class ProductVectorClientFallback implements FallbackFactory<ProductVectorClient> {

    /** 摄取降级哨兵（与知识库 upsert 哨兵口径一致）。 */
    public static final int UPSERT_FALLBACK_SENTINEL = -1;

    @Override
    public ProductVectorClient create(Throwable cause) {
        return new ProductVectorClient() {

            @Override
            public int upsert(ProductVectorUpsertCO co) {
                log.warn("商品向量摄取降级：search-service 不可用，本商品向量未更新（可由 reindex 补齐）"
                        + " productId={}", co == null ? null : co.getProductId(), cause);
                return UPSERT_FALLBACK_SENTINEL;
            }

            @Override
            public List<ProductVectorHitCO> similar(Long productId, int size) {
                log.warn("相似商品检索降级：search-service 不可用，推荐位返回空 productId={}", productId, cause);
                return List.of();
            }
        };
    }
}
