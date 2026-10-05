package org.icedamericanomall.item.app;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.ProductVectorClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.ProductVectorHitCO;
import org.icedamericanomall.item.adapter.assembler.ProductConverter;
import org.icedamericanomall.item.adapter.assembler.SkuConverter;
import org.icedamericanomall.item.adapter.request.CreateProductReq;
import org.icedamericanomall.item.adapter.request.UpdateProductReq;
import org.icedamericanomall.item.adapter.response.ProductVO;
import org.icedamericanomall.item.adapter.response.RecommendProductVO;
import org.icedamericanomall.item.adapter.response.SkuVO;
import org.icedamericanomall.item.domain.enums.ProductStatusEnum;
import org.icedamericanomall.item.domain.model.product.Product;
import org.icedamericanomall.item.domain.model.sku.Sku;
import org.icedamericanomall.item.domain.repository.ProductRepository;
import org.icedamericanomall.item.domain.repository.SkuRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品编排（应用层）—— 列表/详情装配、发布（聚合+SKU 一并落库）、状态与编辑事务边界、
 * 相似商品推荐组装（P27，ADR-046）。
 *
 * <p>列表项只回填最低价（一次批量聚合，无 N+1）；{@code sort=price} 由数据库按
 * 在售 SKU 最低价排序，保证跨页顺序正确。
 *
 * <p>向量摄取旁路由 {@link ProductRecommendManager} 收口（事务外 afterCommit / 直发）；
 * 相似检索（Feign）与本类其余 Feign 依赖一样只允许出现在应用层。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductManager {

    /** 相似推荐缺省条数（P27 契约：缺省 5）。 */
    private static final int DEFAULT_SIMILAR_SIZE = 5;
    /** 相似推荐单次上限（P27 契约：≤20 归一）。 */
    private static final int MAX_SIMILAR_SIZE = 20;

    private final ProductRepository productRepository;
    private final SkuRepository skuRepository;
    private final ProductConverter productConverter;
    private final SkuConverter skuConverter;
    private final FreightTemplateManager freightTemplateManager;
    private final SkuManager skuManager;
    private final ProductVectorClient productVectorClient;
    private final ProductRecommendManager productRecommendManager;

    /** 商品分页（在售）：sort=price 走最低价排序 SQL，否则销量/广告优先。 */
    public IPage<ProductVO> pageProducts(Long sellerId, Long categoryId, String keyword,
                                         String sort, String order, int page, int size) {
        IPage<Product> productPage;
        if ("price".equals(sort)) {
            productPage = productRepository.pageOrderByMinPrice(page, size,
                    sellerId, categoryId, keyword,
                    "asc".equals(order) ? "ASC" : "DESC");
        } else {
            productPage = productRepository.pageOnSale(sellerId, categoryId, keyword, sort, page, size);
        }
        IPage<ProductVO> voPage = productPage.convert(productConverter::toVO);
        fillMinPrice(voPage.getRecords());
        return voPage;
    }

    /** 商品详情（含全部 SKU 与在售最低价）；DELETED 视为不存在。 */
    public ProductVO getProductDetail(Long id) {
        Product product = productRepository.findById(id);
        if (product == null || ProductStatusEnum.DELETED.getCode() == product.getStatus()) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        ProductVO vo = productConverter.toVO(product);
        List<Sku> skus = skuRepository.findByProductId(id);
        vo.setSkus(skuConverter.toVOs(skus));
        vo.setMinPrice(minPriceOf(skus));
        return vo;
    }

    /** 商品发布（F2：绑定模板归属校验；SKU 经聚合工厂归一热销元数据）。 */
    @Transactional(rollbackFor = Exception.class)
    public void createProduct(Long sellerId, CreateProductReq req) {
        if (req.getFreightTemplateId() != null) {
            freightTemplateManager.requireBindable(sellerId, req.getFreightTemplateId());
        }
        Product product = Product.publish(sellerId, req.getCategoryId(), req.getFreightTemplateId(),
                req.getName(), req.getMainImage(), req.getDescription(), req.getBrand());
        product.setSoldCount(0);
        product.setCommentCount(0);
        product.setStatus(ProductStatusEnum.ON_SHELF.getCode());
        product.setPublishTime(LocalDateTime.now());
        Product saved = productRepository.save(product);

        for (CreateProductReq.SkuReq skuReq : req.getSkus()) {
            Sku sku = Sku.create(product.getId(), skuReq.getSpec(), skuReq.getPrice(),
                    skuReq.getStock(), skuReq.getImage(), skuReq.getStockType(),
                    skuReq.getIsHot(), skuReq.getHotReason(), skuReq.getSalesTags());
            skuRepository.save(sku);
        }

        // 向量摄取钩子（ADR-046）：CO 取自本次落库聚合，事务提交后才旁路发送——回滚即不摄取
        productRecommendManager.ingestAfterCommit(saved, onSaleMinPrice(saved.getId()));
    }

    /** 上下架/删除状态变更。 */
    public void updateProductStatus(Long productId, Integer status) {
        if (!productRepository.existsById(productId)) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        productRepository.updateStatus(productId, status);
    }

    /** 商家编辑商品（归属校验；F2：运费绑定非空即改，须归属当前商家）。 */
    public void updateProduct(Long productId, Long sellerId, UpdateProductReq req) {
        Product product = productRepository.findById(productId);
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        if (!product.getSellerId().equals(sellerId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权修改该商品");
        }
        if (req.getFreightTemplateId() != null) {
            freightTemplateManager.requireBindable(sellerId, req.getFreightTemplateId());
        }
        productRepository.updateEditable(productId, req.getName(), req.getMainImage(),
                req.getDescription(), req.getBrand(), req.getCategoryId(), req.getFreightTemplateId());

        // 向量摄取钩子（ADR-046）：PATCH 后重读合并态（null 字段保留原值），无活动事务 → 直接旁路发送
        Product merged = productRepository.findById(productId);
        productRecommendManager.ingestAfterCommit(merged, onSaleMinPrice(productId));
    }

    /** 商品在售 SKU 视图。 */
    public List<SkuVO> listSkus(Long productId) {
        return skuConverter.toVOs(skuRepository.findOnSaleByProductId(productId));
    }

    // ==================== 相似商品推荐（P27，ADR-046，纯旁路 fail-open） ====================

    /**
     * 相似商品推荐 —— 向量 kNN 命中 id 后批量查本地库，组装新鲜在售商品卡。
     *
     * <p>fail-open（纯旁路）：client 降级已兜底空列表，此处再防御性兜底任意未预期异常
     * （ReviewManager 触达先例口径）；命中商品<b>恒过滤在售</b>并剔除自身/已删除，
     * 按命中顺序（= 相似度降序）组装，不足 size 按实际数量返回——推荐位可空。
     *
     * @param productId 锚点商品 id
     * @param size      期望条数（null/非正 → 缺省 5，&gt;20 归一为 20）
     * @return 相似商品卡（相似度降序）；无命中/降级时为空列表，不报错
     */
    public List<RecommendProductVO> similarProducts(Long productId, Integer size) {
        List<ProductVectorHitCO> hits = similarSafely(productId, normalizeSize(size));
        if (hits.isEmpty()) {
            return List.of();
        }
        List<Long> hitIds = hits.stream().map(ProductVectorHitCO::getProductId).toList();
        Map<Long, Product> cardById = onShelfCardsByIds(hitIds);
        cardById.remove(productId);
        Map<Long, Integer> minPriceMap = cardById.isEmpty() ? Map.of()
                : skuManager.getMinPriceMapByProductIds(List.copyOf(cardById.keySet()));
        return hitIds.stream()
                .filter(cardById::containsKey)
                .map(id -> toRecommendVO(cardById.get(id), minPriceMap.get(id)))
                .toList();
    }

    // ==================== 内部契约回填（I40/I42/I43 消费方） ====================

    public Map<Long, Long> getSellerIdMapByProductIds(List<Long> productIds) {
        return productRepository.sellerIdMapByProductIds(productIds);
    }

    public Map<Long, Long> getCategoryIdMapByProductIds(List<Long> productIds) {
        return productRepository.categoryIdMapByProductIds(productIds);
    }

    public Map<Long, Long> getFreightTemplateIdMapByProductIds(List<Long> productIds) {
        return productRepository.freightTemplateIdMapByProductIds(productIds);
    }

    // ==================== 私有方法 ====================

    /** 一次批量聚合最低价，避免逐商品查询（N+1）。 */
    private void fillMinPrice(List<ProductVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<Long, Integer> minPriceMap = skuManager.getMinPriceMapByProductIds(
                records.stream().map(ProductVO::getId).toList());
        records.forEach(vo -> vo.setMinPrice(minPriceMap.get(vo.getId())));
    }

    /** 商品在售 SKU 的最低价（分），无在售 SKU 时返回 null。 */
    private Integer minPriceOf(List<Sku> skus) {
        return skus.stream()
                .filter(Sku::isOnSale)
                .filter(sku -> sku.getPrice() != null)
                .map(Sku::getPrice)
                .min(Integer::compareTo)
                .orElse(null);
    }

    // ==================== 相似推荐私有装配 ====================

    /** 相似检索防御层：client 降级已兜底空列表，再兜任意未预期异常，推荐位返回空。 */
    private List<ProductVectorHitCO> similarSafely(Long productId, int size) {
        try {
            return productVectorClient.similar(productId, size);
        } catch (Exception e) {
            log.warn("相似商品检索失败，推荐位返回空: productId={}, size={}, cause={}",
                    productId, size, e.getMessage());
            return List.of();
        }
    }

    /** size 归一（P27 契约：缺省 5、≤20；非正按缺省处理，与服务端 I48 归一口径一致）。 */
    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIMILAR_SIZE;
        }
        return Math.min(size, MAX_SIMILAR_SIZE);
    }

    /**
     * 批量载入命中商品，仅保留在售（下架/删除一并剔除）；id → 聚合。
     * <p>最终组装顺序由命中 id 序列（相似度降序）恢复，本映射无需保序。
     */
    private Map<Long, Product> onShelfCardsByIds(List<Long> hitIds) {
        return productRepository.findAllByIds(hitIds).stream()
                .filter(p -> p.getStatus() != null
                        && p.getStatus() == ProductStatusEnum.ON_SHELF.getCode())
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
    }

    /** 命中商品 → 推荐卡（字段集对齐商品列表卡：展示价 = 在售 SKU 最低价）。 */
    private RecommendProductVO toRecommendVO(Product product, Integer minPrice) {
        RecommendProductVO vo = new RecommendProductVO();
        vo.setProductId(product.getId());
        vo.setName(product.getName());
        vo.setMainImage(product.getMainImage());
        vo.setPrice(minPrice);
        vo.setSoldCount(product.getSoldCount());
        return vo;
    }

    /** 单商品在售 SKU 最低价（分，对齐商品列表卡 minPrice 口径）；无在售 SKU 时为 null。 */
    private Integer onSaleMinPrice(Long productId) {
        return skuManager.getMinPriceMapByProductIds(List.of(productId)).get(productId);
    }
}
