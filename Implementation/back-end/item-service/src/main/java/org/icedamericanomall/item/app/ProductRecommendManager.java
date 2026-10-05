package org.icedamericanomall.item.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.ProductVectorClient;
import org.icedamericanomall.dto.ProductVectorUpsertCO;
import org.icedamericanomall.fallback.ProductVectorClientFallback;
import org.icedamericanomall.item.domain.model.product.Product;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 商品向量推荐协作器（ADR-046 / ADR-025 用例族）—— 向量摄取旁路的唯一收口。
 *
 * <p><b>事务纪律（硬约束：Feign 绝不入事务）</b>：摄取发生在商品落库成功<b>之后</b>——
 * <ul>
 *   <li>调用方处于活动事务（如 {@code ProductManager#createProduct}）→ 注册
 *       {@code afterCommit} 回调，事务提交后才发送：回滚即不摄取；
 *       CO 在注册时由本次落库的聚合快照构建，字段值在提交前冻结；</li>
 *   <li>无活动事务（如 {@code ProductManager#updateProduct} 单语句路径）→ 直接发送。</li>
 * </ul>
 * 发送为 fail-open：降级哨兵 {@code -1} / 任何异常只记日志，商品主流程零感知
 * （对齐 trade {@code OrderCreationManager} afterCommit 先例与 ReviewManager 触达先例；
 * 向量可由后续编辑或 reindex 全量重建补齐）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductRecommendManager {

    private final ProductVectorClient productVectorClient;

    /**
     * 商品向量摄取（契约 I47 幂等覆盖写，事务外旁路）。
     *
     * <p>未落库（id 为空）或已删除的商品静默跳过，不产生任何远程调用。
     *
     * @param product  刚落库的商品聚合（含技术主键；deleted 态跳过）
     * @param minPrice 在售 SKU 最低价（分，可为 null）
     */
    public void ingestAfterCommit(Product product, Integer minPrice) {
        if (product == null || product.getId() == null || product.isDeleted()) {
            log.debug("商品向量摄取跳过：商品未落库或已删除");
            return;
        }
        ProductVectorUpsertCO co = buildUpsertCO(product, minPrice);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sendSafely(co);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendSafely(co);
            }
        });
    }

    // ==================== 私有方法 ====================

    /** 聚合快照 → 摄取 CO（{@code productId} 为幂等键，重写整份商品向量文档）。 */
    private ProductVectorUpsertCO buildUpsertCO(Product product, Integer minPrice) {
        ProductVectorUpsertCO co = new ProductVectorUpsertCO();
        co.setProductId(product.getId());
        co.setName(product.getName());
        co.setDescription(product.getDescription());
        co.setBrand(product.getBrand());
        co.setCategoryId(product.getCategoryId());
        co.setMainImage(product.getMainImage());
        co.setPrice(minPrice);
        co.setSoldCount(product.getSoldCount());
        return co;
    }

    /** 旁路发送：降级哨兵 {@code -1} / 异常只记日志，主流程零感知（fail-open，ADR-046）。 */
    private void sendSafely(ProductVectorUpsertCO co) {
        try {
            int result = productVectorClient.upsert(co);
            if (result == ProductVectorClientFallback.UPSERT_FALLBACK_SENTINEL) {
                log.warn("商品向量摄取降级（哨兵-1），向量未更新可由 reindex 补齐: productId={}",
                        co.getProductId());
            } else {
                log.debug("商品向量摄取完成: productId={}, result={}", co.getProductId(), result);
            }
        } catch (Exception e) {
            log.warn("商品向量摄取失败，商品主流程不受影响: productId={}", co.getProductId(), e);
        }
    }
}
