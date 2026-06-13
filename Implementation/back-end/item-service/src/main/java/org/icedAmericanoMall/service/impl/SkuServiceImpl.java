package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.icedAmericanoMall.domain.entity.SkuEntity;
import org.icedAmericanoMall.mapper.SkuMapper;
import org.icedAmericanoMall.service.SkuService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SkuServiceImpl extends ServiceImpl<SkuMapper, SkuEntity> implements SkuService {

    @Override
    public List<SkuEntity> getSkuListByIds(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        return lambdaQuery().in(SkuEntity::getId, skuIds).list();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deductStock(Long skuId, int quantity) {
        SkuEntity sku = getById(skuId);
        if (sku == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "SKU不存在");
        }
        if (sku.getStock() < quantity) {
            throw new BizException(ErrorCode.BALANCE_INSUFFICIENT, "库存不足");
        }
        boolean updated = lambdaUpdate()
                .eq(SkuEntity::getId, skuId)
                .eq(SkuEntity::getVersion, sku.getVersion())
                .set(SkuEntity::getStock, sku.getStock() - quantity)
                .setIncrBy(SkuEntity::getSoldCount, quantity)
                .update();
        if (!updated) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "库存扣减失败，请重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreStock(Long skuId, int quantity) {
        SkuEntity sku = getById(skuId);
        if (sku == null) {
            return;
        }
        lambdaUpdate()
                .eq(SkuEntity::getId, skuId)
                .setIncrBy(SkuEntity::getStock, quantity)
                .update();
    }
}
