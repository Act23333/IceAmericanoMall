package org.icedamericanomall.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.icedamericanomall.convert.ProductConverter;
import org.icedamericanomall.convert.SkuConverter;
import org.icedamericanomall.domain.dto.CreateProductReq;
import org.icedamericanomall.domain.dto.UpdateProductReq;
import org.icedamericanomall.domain.entity.ProductEntity;
import org.icedamericanomall.domain.entity.SkuEntity;
import org.icedamericanomall.domain.vo.ProductVO;
import org.icedamericanomall.domain.vo.SkuVO;
import org.icedamericanomall.enums.ProductStatusEnum;
import org.icedamericanomall.mapper.ProductMapper;
import org.icedamericanomall.service.ProductService;
import org.icedamericanomall.service.SkuService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, ProductEntity> implements ProductService {

    private final SkuService skuService;
    private final ProductConverter productConverter;
    private final SkuConverter skuConverter;

    public ProductServiceImpl(SkuService skuService, ProductConverter productConverter, SkuConverter skuConverter) {
        this.skuService = skuService;
        this.productConverter = productConverter;
        this.skuConverter = skuConverter;
    }

    @Override
    public IPage<ProductVO> pageProducts(Long categoryId, String keyword, String sort, String order, int page, int size) {
        LambdaQueryWrapper<ProductEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductEntity::getStatus, ProductStatusEnum.ON_SHELF.getCode());
        if (categoryId != null) {
            wrapper.eq(ProductEntity::getCategoryId, categoryId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(ProductEntity::getName, keyword);
        }
        if ("sales".equals(sort)) {
            wrapper.orderByDesc(ProductEntity::getSoldCount);
        } else if ("price".equals(sort)) {
            wrapper.orderBy(true, "asc".equals(order), ProductEntity::getId);
        } else {
            // Default: ad products first, then by create_time
            wrapper.orderByDesc(ProductEntity::getIsAd)
                   .orderByDesc(ProductEntity::getCreateTime);
        }

        IPage<ProductEntity> entityPage = page(new Page<>(page, size), wrapper);
        IPage<ProductVO> voPage = entityPage.convert(productConverter::entityToVO);

        // 补充 SKU 最低价格 (列表页不需要完整 SKU 列表, 只需 minPrice)
        if (!voPage.getRecords().isEmpty()) {
            List<Long> productIds = voPage.getRecords().stream().map(ProductVO::getId).toList();
            Map<Long, List<SkuEntity>> skuMap = skuService.lambdaQuery()
                    .in(SkuEntity::getProductId, productIds)
                    .eq(SkuEntity::getStatus, 1)
                    .list()
                    .stream()
                    .collect(Collectors.groupingBy(SkuEntity::getProductId));
            voPage.getRecords().forEach(vo -> {
                List<SkuEntity> skus = skuMap.getOrDefault(vo.getId(), Collections.emptyList());
                vo.setSkus(skuConverter.entitiesToVOs(skus));
            });
        }
        return voPage;
    }

    @Override
    public ProductVO getProductDetail(Long id) {
        ProductEntity product = getById(id);
        if (product == null || ProductStatusEnum.DELETED.getCode() == product.getStatus()) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "商品不存在");
        }
        ProductVO vo = productConverter.entityToVO(product);
        List<SkuEntity> skus = skuService.lambdaQuery()
                .eq(SkuEntity::getProductId, id)
                .list();
        List<SkuVO> skuVOs = skuConverter.entitiesToVOs(skus);
        vo.setSkus(skuVOs);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createProduct(Long sellerId, CreateProductReq req) {
        ProductEntity product = new ProductEntity();
        product.setProductId(IdUtil.fastSimpleUUID());
        product.setSellerId(sellerId);
        product.setCategoryId(req.getCategoryId());
        product.setName(req.getName());
        product.setMainImage(req.getMainImage());
        product.setDescription(req.getDescription());
        product.setBrand(req.getBrand());
        product.setSoldCount(0);
        product.setCommentCount(0);
        product.setStatus(ProductStatusEnum.ON_SHELF.getCode());
        product.setPublishTime(LocalDateTime.now());
        save(product);

        for (CreateProductReq.SkuReq skuReq : req.getSkus()) {
            SkuEntity sku = new SkuEntity();
            sku.setSkuId(IdUtil.fastSimpleUUID());
            sku.setProductId(product.getId());
            sku.setSpec(skuReq.getSpec());
            sku.setPrice(skuReq.getPrice());
            sku.setStock(skuReq.getStock());
            sku.setImage(skuReq.getImage());
            sku.setSoldCount(0);
            sku.setStatus(1);
            skuService.save(sku);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProductStatus(Long productId, Integer status) {
        ProductEntity product = getById(productId);
        if (product == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "商品不存在");
        }
        lambdaUpdate()
                .eq(ProductEntity::getId, productId)
                .set(ProductEntity::getStatus, status)
                .update();
    }

    @Override
    public Map<Long, Long> getSellerIdMapByProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return lambdaQuery()
                .in(ProductEntity::getId, productIds)
                .list()
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        ProductEntity::getId,
                        ProductEntity::getSellerId,
                        (a, b) -> a));
    }

    @Override
    public Map<Long, Long> getCategoryIdMapByProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return lambdaQuery()
                .in(ProductEntity::getId, productIds)
                .list()
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        ProductEntity::getId,
                        ProductEntity::getCategoryId,
                        (a, b) -> a));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProduct(Long productId, Long sellerId, UpdateProductReq req) {
        ProductEntity product = getById(productId);
        if (product == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "商品不存在");
        }
        if (!product.getSellerId().equals(sellerId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权修改该商品");
        }
        lambdaUpdate()
                .eq(ProductEntity::getId, productId)
                .set(req.getName() != null, ProductEntity::getName, req.getName())
                .set(req.getMainImage() != null, ProductEntity::getMainImage, req.getMainImage())
                .set(req.getDescription() != null, ProductEntity::getDescription, req.getDescription())
                .set(req.getBrand() != null, ProductEntity::getBrand, req.getBrand())
                .set(req.getCategoryId() != null, ProductEntity::getCategoryId, req.getCategoryId())
                .update();
    }

    @Override
    public List<org.icedamericanomall.domain.entity.SkuEntity> listSkus(Long productId) {
        return skuService.lambdaQuery()
                .eq(org.icedamericanomall.domain.entity.SkuEntity::getProductId, productId)
                .eq(org.icedamericanomall.domain.entity.SkuEntity::getStatus, 1)
                .list();
    }
}
