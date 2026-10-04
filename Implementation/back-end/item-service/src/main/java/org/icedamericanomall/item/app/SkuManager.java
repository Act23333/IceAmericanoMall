package org.icedamericanomall.item.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.item.adapter.assembler.SkuConverter;
import org.icedamericanomall.item.adapter.response.SkuVO;
import org.icedamericanomall.item.domain.model.sku.Sku;
import org.icedamericanomall.item.domain.repository.SkuRepository;
import org.icedamericanomall.dto.SkuCO;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SKU 编排（应用层）—— 原子扣减/恢复编排（不限量分支判定）与内部契约回填。
 *
 * <p><b>并发语义（ADR-020）</b>：库存校验与扣减在同一条 UPDATE 内完成，
 * 不限量的 SKU 仅累加销量；受影响行数为 0 即库存不足。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkuManager {

    private final SkuRepository skuRepository;
    private final SkuConverter skuConverter;

    /** 批量查 SKU 视图（adapter 内部契约）。 */
    public List<SkuVO> getViewsByIds(List<Long> skuIds) {
        return skuConverter.toVOs(skuRepository.findByIds(skuIds));
    }

    /** 批量查 SKU 内部 CO（trade 下单取数）。 */
    public List<SkuCO> getCosByIds(List<Long> skuIds) {
        return skuConverter.toCOs(skuRepository.findByIds(skuIds));
    }

    /** 商品在售 SKU 视图（商品详情）。 */
    public List<SkuVO> listOnSaleByProduct(Long productId) {
        return skuConverter.toVOs(skuRepository.findOnSaleByProductId(productId));
    }

    /** 商品全部 SKU 视图（详情页含下架）。 */
    public List<SkuVO> listByProduct(Long productId) {
        return skuConverter.toVOs(skuRepository.findByProductId(productId));
    }

    /**
     * 原子扣库存：SKU 不存在 → SKU_NOT_FOUND；行数 0 → STOCK_INSUFFICIENT；
     * 不限量 SKU 仅累加销量。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deductStock(Long skuId, int quantity) {
        Sku sku = skuRepository.findById(skuId).orElse(null);
        if (sku == null) {
            throw new BizException(ErrorCode.SKU_NOT_FOUND);
        }
        int rows = sku.isUnlimited()
                ? skuRepository.increaseSoldCount(skuId, quantity)
                : skuRepository.deductStock(skuId, quantity);
        if (rows == 0) {
            log.warn("库存扣减未命中（库存不足或已停售）: skuId={}, quantity={}", skuId, quantity);
            throw new BizException(ErrorCode.STOCK_INSUFFICIENT);
        }
    }

    /**
     * 原子恢复库存：SKU 已删除时记 warn 后幂等返回（订单取消重试链路不抛异常）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void restoreStock(Long skuId, int quantity) {
        Sku sku = skuRepository.findById(skuId).orElse(null);
        if (sku == null) {
            log.warn("SKU不存在，跳过库存恢复: skuId={}, quantity={}", skuId, quantity);
            return;
        }
        int rows = sku.isUnlimited()
                ? skuRepository.decreaseSoldCount(skuId, quantity)
                : skuRepository.restoreStock(skuId, quantity);
        if (rows == 0) {
            log.warn("库存恢复未命中，按幂等跳过: skuId={}, quantity={}", skuId, quantity);
        }
    }

    /** 批量在售 SKU 最低价映射（商品列表回填，禁止 N+1）。 */
    public Map<Long, Integer> getMinPriceMapByProductIds(List<Long> productIds) {
        return skuRepository.findOnSaleByProductIds(productIds).stream()
                .filter(sku -> sku.getPrice() != null)
                .collect(Collectors.toMap(
                        Sku::getProductId,
                        Sku::getPrice,
                        Math::min));
    }
}
