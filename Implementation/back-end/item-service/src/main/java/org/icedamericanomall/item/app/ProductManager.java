package org.icedamericanomall.item.app;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.item.adapter.assembler.ProductConverter;
import org.icedamericanomall.item.adapter.assembler.SkuConverter;
import org.icedamericanomall.item.adapter.request.CreateProductReq;
import org.icedamericanomall.item.adapter.request.UpdateProductReq;
import org.icedamericanomall.item.adapter.response.ProductVO;
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

/**
 * 商品编排（应用层）—— 列表/详情装配、发布（聚合+SKU 一并落库）、状态与编辑事务边界。
 *
 * <p>列表项只回填最低价（一次批量聚合，无 N+1）；{@code sort=price} 由数据库按
 * 在售 SKU 最低价排序，保证跨页顺序正确。
 */
@Component
@RequiredArgsConstructor
public class ProductManager {

    private final ProductRepository productRepository;
    private final SkuRepository skuRepository;
    private final ProductConverter productConverter;
    private final SkuConverter skuConverter;
    private final FreightTemplateManager freightTemplateManager;
    private final SkuManager skuManager;

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
        productRepository.save(product);

        for (CreateProductReq.SkuReq skuReq : req.getSkus()) {
            Sku sku = Sku.create(product.getId(), skuReq.getSpec(), skuReq.getPrice(),
                    skuReq.getStock(), skuReq.getImage(), skuReq.getStockType(),
                    skuReq.getIsHot(), skuReq.getHotReason(), skuReq.getSalesTags());
            skuRepository.save(sku);
        }
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
    }

    /** 商品在售 SKU 视图。 */
    public List<SkuVO> listSkus(Long productId) {
        return skuConverter.toVOs(skuRepository.findOnSaleByProductId(productId));
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
}
