package org.icedamericanomall.item.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.item.domain.enums.ProductStatusEnum;
import org.icedamericanomall.item.domain.model.product.Product;
import org.icedamericanomall.item.domain.repository.ProductRepository;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 商品仓储实现 —— MyBatis-Plus 访问 {@code product} 表，PO ↔ 聚合转换内聚于此。
 * {@code sort=price} 的跨页排序走 {@link ProductMapper#selectPageOrderByMinPrice} 自定义 SQL。
 */
@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductMapper productMapper;

    @Override
    public IPage<Product> pageOnSale(Long sellerId, Long categoryId, String keyword, String sort,
                                     int page, int size) {
        LambdaQueryWrapper<ProductPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductPO::getStatus, ProductStatusEnum.ON_SHELF.getCode());
        if (sellerId != null) {
            // S5：店铺页商品列表（按商家过滤）
            wrapper.eq(ProductPO::getSellerId, sellerId);
        }
        if (categoryId != null) {
            wrapper.eq(ProductPO::getCategoryId, categoryId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(ProductPO::getName, keyword);
        }
        if ("sales".equals(sort)) {
            wrapper.orderByDesc(ProductPO::getSoldCount);
        } else {
            // Default: ad products first, then by create_time
            wrapper.orderByDesc(ProductPO::getIsAd)
                   .orderByDesc(ProductPO::getCreateTime);
        }
        return productMapper.selectPage(new Page<>(page, size), wrapper)
                .convert(ProductRepositoryImpl::toDomain);
    }

    /** 全状态商品分页（AD64 重建真相源扫描）：id 升序保证跨页稳定遍历。 */
    @Override
    public IPage<Product> pageAll(int pageNum, int pageSize) {
        return productMapper.selectPage(new Page<>(pageNum, pageSize),
                        new LambdaQueryWrapper<ProductPO>().orderByAsc(ProductPO::getId))
                .convert(ProductRepositoryImpl::toDomain);
    }

    @Override
    public IPage<Product> pageOrderByMinPrice(int page, int size, Long sellerId, Long categoryId,
                                              String keyword, String orderDirection) {
        return productMapper.selectPageOrderByMinPrice(new Page<>(page, size),
                        ProductStatusEnum.ON_SHELF.getCode(),
                        org.icedamericanomall.item.domain.enums.SkuStatusEnum.ON_SALE.getCode(),
                        sellerId, categoryId, keyword, orderDirection)
                .convert(ProductRepositoryImpl::toDomain);
    }

    @Override
    public boolean existsById(Long productId) {
        return productMapper.selectById(productId) != null;
    }

    @Override
    public void incrementCommentCount(Long productId) {
        productMapper.update(null, new LambdaUpdateWrapper<ProductPO>()
                .eq(ProductPO::getId, productId)
                .setSql("comment_count = comment_count + 1"));
    }

    @Override
    public Product findById(Long id) {
        ProductPO po = productMapper.selectById(id);
        return po == null ? null : toDomain(po);
    }

    @Override
    public List<Product> findAllByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return productMapper.selectList(new LambdaQueryWrapper<ProductPO>()
                        .in(ProductPO::getId, ids))
                .stream().map(ProductRepositoryImpl::toDomain).toList();
    }

    @Override
    public Product save(Product product) {
        ProductPO po = toPO(product);
        productMapper.insert(po);
        product.setId(po.getId());
        return product;
    }

    @Override
    public void updateStatus(Long productId, Integer status) {
        productMapper.update(null, new LambdaUpdateWrapper<ProductPO>()
                .eq(ProductPO::getId, productId)
                .set(ProductPO::getStatus, status));
    }

    @Override
    public void updateEditable(Long productId, String name, String mainImage, String description,
                               String brand, Long categoryId, Long freightTemplateId) {
        productMapper.update(null, new LambdaUpdateWrapper<ProductPO>()
                .eq(ProductPO::getId, productId)
                .set(name != null, ProductPO::getName, name)
                .set(mainImage != null, ProductPO::getMainImage, mainImage)
                .set(description != null, ProductPO::getDescription, description)
                .set(brand != null, ProductPO::getBrand, brand)
                .set(categoryId != null, ProductPO::getCategoryId, categoryId)
                .set(freightTemplateId != null, ProductPO::getFreightTemplateId, freightTemplateId));
    }

    @Override
    public Map<Long, Long> sellerIdMapByProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return productMapper.selectList(new LambdaQueryWrapper<ProductPO>()
                        .in(ProductPO::getId, productIds))
                .stream().collect(Collectors.toMap(
                        ProductPO::getId, ProductPO::getSellerId, (a, b) -> a));
    }

    @Override
    public Map<Long, Long> categoryIdMapByProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return productMapper.selectList(new LambdaQueryWrapper<ProductPO>()
                        .in(ProductPO::getId, productIds))
                .stream().collect(Collectors.toMap(
                        ProductPO::getId, ProductPO::getCategoryId, (a, b) -> a));
    }

    /**
     * 未绑定模板（freight_template_id 为 NULL）的商品<b>不进入</b>映射——
     * 调用方取值得到 {@code null}，语义即「未绑定 → 回落商家默认模板」。
     */
    @Override
    public Map<Long, Long> freightTemplateIdMapByProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return productMapper.selectList(new LambdaQueryWrapper<ProductPO>()
                        .in(ProductPO::getId, productIds))
                .stream()
                .filter(po -> po.getFreightTemplateId() != null)
                .collect(Collectors.toMap(
                        ProductPO::getId, ProductPO::getFreightTemplateId, (a, b) -> a));
    }

    static Product toDomain(ProductPO po) {
        return Product.builder()
                .id(po.getId()).productId(po.getProductId()).sellerId(po.getSellerId())
                .categoryId(po.getCategoryId()).freightTemplateId(po.getFreightTemplateId())
                .name(po.getName()).mainImage(po.getMainImage()).images(po.getImages())
                .videoUrl(po.getVideoUrl()).attributes(po.getAttributes())
                .serviceTags(po.getServiceTags()).salesTags(po.getSalesTags())
                .viewCount(po.getViewCount()).description(po.getDescription())
                .brand(po.getBrand()).soldCount(po.getSoldCount()).commentCount(po.getCommentCount())
                .isAd(po.getIsAd()).status(po.getStatus())
                .publishTime(po.getPublishTime()).createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    static ProductPO toPO(Product product) {
        ProductPO po = new ProductPO();
        po.setId(product.getId());
        po.setProductId(product.getProductId());
        po.setSellerId(product.getSellerId());
        po.setCategoryId(product.getCategoryId());
        po.setFreightTemplateId(product.getFreightTemplateId());
        po.setName(product.getName());
        po.setMainImage(product.getMainImage());
        po.setImages(product.getImages());
        po.setVideoUrl(product.getVideoUrl());
        po.setAttributes(product.getAttributes());
        po.setServiceTags(product.getServiceTags());
        po.setSalesTags(product.getSalesTags());
        po.setViewCount(product.getViewCount());
        po.setDescription(product.getDescription());
        po.setBrand(product.getBrand());
        po.setSoldCount(product.getSoldCount());
        po.setCommentCount(product.getCommentCount());
        po.setIsAd(product.getIsAd());
        po.setStatus(product.getStatus());
        po.setPublishTime(product.getPublishTime());
        return po;
    }
}
