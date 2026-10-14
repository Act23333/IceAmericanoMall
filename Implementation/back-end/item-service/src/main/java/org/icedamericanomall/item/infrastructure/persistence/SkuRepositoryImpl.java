package org.icedamericanomall.item.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.icedamericanomall.common.utils.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.item.domain.enums.SkuStatusEnum;
import org.icedamericanomall.item.domain.model.sku.Sku;
import org.icedamericanomall.item.domain.repository.SkuRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * SKU 仓储实现 —— MyBatis-Plus 访问 {@code sku} 表，PO ↔ 聚合转换内聚于此。
 * 原子扣减/恢复 SQL（ADR-020）在 {@link SkuMapper}，原样保留。
 */
@Repository
@RequiredArgsConstructor
public class SkuRepositoryImpl implements SkuRepository {

    private final SkuMapper skuMapper;

    @Override
    public java.util.Optional<Sku> findById(Long skuId) {
        SkuPO po = skuMapper.selectById(skuId);
        return po == null ? java.util.Optional.empty() : java.util.Optional.of(toDomain(po));
    }

    @Override
    public List<Sku> findByIds(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        return skuMapper.selectList(new LambdaQueryWrapper<SkuPO>()
                        .in(SkuPO::getId, skuIds))
                .stream().map(SkuRepositoryImpl::toDomain).toList();
    }

    @Override
    public List<Sku> findByProductId(Long productId) {
        return skuMapper.selectList(new LambdaQueryWrapper<SkuPO>()
                        .eq(SkuPO::getProductId, productId))
                .stream().map(SkuRepositoryImpl::toDomain).toList();
    }

    @Override
    public List<Sku> findOnSaleByProductId(Long productId) {
        return skuMapper.selectList(new LambdaQueryWrapper<SkuPO>()
                        .eq(SkuPO::getProductId, productId)
                        .eq(SkuPO::getStatus, SkuStatusEnum.ON_SALE.getCode()))
                .stream().map(SkuRepositoryImpl::toDomain).toList();
    }

    @Override
    public List<Sku> findOnSaleByProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return skuMapper.selectList(new LambdaQueryWrapper<SkuPO>()
                        .in(SkuPO::getProductId, productIds)
                        .eq(SkuPO::getStatus, SkuStatusEnum.ON_SALE.getCode()))
                .stream().map(SkuRepositoryImpl::toDomain).toList();
    }

    @Override
    public Sku save(Sku sku) {
        SkuPO po = toPO(sku);
        skuMapper.insert(po);
        sku.setId(po.getId());
        return sku;
    }

    @Override
    public int deductStock(Long skuId, int quantity) {
        return skuMapper.deductStock(skuId, quantity);
    }

    @Override
    public int restoreStock(Long skuId, int quantity) {
        return skuMapper.restoreStock(skuId, quantity);
    }

    @Override
    public int increaseSoldCount(Long skuId, int quantity) {
        return skuMapper.increaseSoldCount(skuId, quantity);
    }

    @Override
    public int decreaseSoldCount(Long skuId, int quantity) {
        return skuMapper.decreaseSoldCount(skuId, quantity);
    }

    static SkuPO toPO(Sku sku) {
        SkuPO po = new SkuPO();
        po.setId(sku.getId());
        po.setSkuId(IdGenerator.toBytes(sku.getSkuId()));
        po.setProductId(sku.getProductId());
        po.setSpec(sku.getSpec());
        po.setPrice(sku.getPrice());
        po.setOriginalPrice(sku.getOriginalPrice());
        po.setStock(sku.getStock());
        po.setImage(sku.getImage());
        po.setSoldCount(sku.getSoldCount());
        po.setStockType(sku.getStockType());
        po.setIsHot(sku.getIsHot());
        po.setHotReason(sku.getHotReason());
        po.setSalesTags(sku.getSalesTags());
        po.setStatus(sku.getStatus());
        po.setVersion(sku.getVersion());
        return po;
    }

    static Sku toDomain(SkuPO po) {
        return Sku.builder()
                .id(po.getId()).skuId(IdGenerator.toHexString(po.getSkuId())).productId(po.getProductId())
                .spec(po.getSpec()).price(po.getPrice()).originalPrice(po.getOriginalPrice())
                .stock(po.getStock()).image(po.getImage()).soldCount(po.getSoldCount())
                .stockType(po.getStockType()).isHot(po.getIsHot()).hotReason(po.getHotReason())
                .salesTags(po.getSalesTags()).status(po.getStatus()).version(po.getVersion())
                .createTime(po.getCreateTime()).updateTime(po.getUpdateTime())
                .build();
    }
}
