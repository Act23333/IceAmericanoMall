package org.icedamericanomall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.domain.entity.SkuEntity;
import org.icedamericanomall.mapper.SkuMapper;
import org.icedamericanomall.service.SkuService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class SkuServiceImpl extends ServiceImpl<SkuMapper, SkuEntity> implements SkuService {

    @Override
    public List<SkuEntity> getSkuListByIds(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        return lambdaQuery().in(SkuEntity::getId, skuIds).list();
    }

    /**
     * <pre>
     * Scenario: 扣减库存（乐观锁）
     *   Given SKU 存在且库存充足
     *   When 调用 deductStock
     *   Then 库存减去指定数量
     *   And 销量增加指定数量
     *   And 使用 @Version 乐观锁防并发超卖
     *
     * Scenario: 库存不足拒绝扣减
     *   Given SKU 库存为 3
     *   When 扣减数量为 5
     *   Then 抛出 BizException "库存不足"
     *
     * Scenario: 乐观锁冲突重试
     *   Given SKU 版本号已变更（被其他事务修改）
     *   When 扣减时版本号不匹配
     *   Then 抛出 BizException "库存扣减失败，请重试"
     * </pre>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deductStock(Long skuId, int quantity) {
        SkuEntity sku = getById(skuId);
        if (sku == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "SKU不存在");
        }
        // V4.0: 不限量商品跳过库存扣减（京东标准），仅增加销量统计
        if (sku.getStockType() != null && sku.getStockType() == 2) { // UNLIMITED
            lambdaUpdate()
                    .eq(SkuEntity::getId, skuId)
                    .setIncrBy(SkuEntity::getSoldCount, quantity)
                    .update();
            return;
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

    /**
     * <pre>
     * Scenario: 恢复库存（乐观锁）
     *   Given SKU 存在
     *   When 订单取消或超时触发库存恢复
     *   Then 库存增加指定数量
     *   And 使用 @Version 乐观锁防并发冲突
     *
     * Scenario: SKU不存在时跳过
     *   Given SKU 已被删除
     *   When 尝试恢复库存
     *   Then 记录 warn 日志并跳过
     * </pre>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreStock(Long skuId, int quantity) {
        SkuEntity sku = getById(skuId);
        if (sku == null) {
            log.warn("SKU不存在，跳过库存恢复: skuId={}", skuId);
            return;
        }
        // V4.0: 不限量商品无需恢复库存
        if (sku.getStockType() != null && sku.getStockType() == 2) { // UNLIMITED
            return;
        }
        boolean updated = lambdaUpdate()
                .eq(SkuEntity::getId, skuId)
                .eq(SkuEntity::getVersion, sku.getVersion())
                .setIncrBy(SkuEntity::getStock, quantity)
                .update();
        if (!updated) {
            log.warn("库存恢复失败（版本冲突），重试中: skuId={}", skuId);
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "库存恢复失败，请重试");
        }
    }
}
