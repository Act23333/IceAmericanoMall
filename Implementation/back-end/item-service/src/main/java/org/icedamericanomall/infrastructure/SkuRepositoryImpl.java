package org.icedamericanomall.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.icedamericanomall.domain.entity.SkuEntity;
import org.icedamericanomall.domain.repository.SkuRepository;
import org.icedamericanomall.mapper.SkuMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * SKU 仓储 MyBatis 实现 — Infrastructure 层
 */
@Repository
public class SkuRepositoryImpl implements SkuRepository {

    private final SkuMapper skuMapper;

    public SkuRepositoryImpl(SkuMapper skuMapper) {
        this.skuMapper = skuMapper;
    }

    @Override
    public Optional<SkuEntity> findById(Long id) {
        return Optional.ofNullable(skuMapper.selectById(id));
    }

    @Override
    public List<SkuEntity> findByProductId(Long productId) {
        return skuMapper.selectList(
                new LambdaQueryWrapper<SkuEntity>().eq(SkuEntity::getProductId, productId));
    }

    @Override
    public int deductStockAtomically(Long skuId, int quantity) {
        return skuMapper.deductStock(skuId, quantity);
    }

    @Override
    public int restoreStockAtomically(Long skuId, int quantity) {
        return skuMapper.restoreStock(skuId, quantity);
    }
}
