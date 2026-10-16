package org.icedamericanomall.item.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.item.adapter.assembler.SkuConverter;
import org.icedamericanomall.item.adapter.response.SkuVO;
import org.icedamericanomall.item.app.dto.SkuSpuDisplay;
import org.icedamericanomall.item.app.port.SkuSpuDisplayQuery;
import org.icedamericanomall.item.domain.model.sku.Sku;
import org.icedamericanomall.item.domain.repository.SkuRepository;
import org.icedamericanomall.dto.SkuCO;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SKU 编排（应用层）—— 原子扣减/恢复编排（不限量分支判定）与内部契约回填。
 *
 * <p><b>并发语义（ADR-020）</b>：库存校验与扣减在同一条 UPDATE 内完成，
 * 不限量的 SKU 仅累加销量；受影响行数为 0 即库存不足。
 *
 * <p><b>I13 商品展示字段（ADR-057）</b>：{@code sku} 表无商品名列，{@code SkuCO} 的
 * {@code product_name/sellerId/categoryId/freightTemplateId} 由
 * {@link SkuSpuDisplayQuery} 联表 {@code spu} <b>整批一次</b>回填（无 N+1）；
 * 回填失败或商品缺失 → 四字段留空（fail-open），SKU 本体照常返回。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkuManager {

    private final SkuRepository skuRepository;
    private final SkuConverter skuConverter;
    private final SkuSpuDisplayQuery skuSpuDisplayQuery;

    /** 批量查 SKU 视图（adapter 内部契约）。 */
    public List<SkuVO> getViewsByIds(List<Long> skuIds) {
        return skuConverter.toVOs(skuRepository.findByIds(skuIds));
    }

    /**
     * 批量查 SKU 内部 CO（I13）—— 购物车回填与 trade 下单取数共用。
     *
     * <p>商品展示四字段（{@code product_name}/{@code seller_id}/{@code category_id}/
     * {@code freight_template_id}）来自 {@code spu} 表，由 {@link #enrichSpuDisplay}
     * 整批回填（单条联表查询）；回填不成功时四字段为 null，SKU 本体照常返回。
     */
    public List<SkuCO> getCosByIds(List<Long> skuIds) {
        List<SkuCO> cos = skuConverter.toCOs(skuRepository.findByIds(skuIds));
        enrichSpuDisplay(cos);
        return cos;
    }

    /** 商品在售 SKU 视图（商品详情）。 */
    public List<SkuVO> listOnSaleBySpu(Long spuId) {
        return skuConverter.toVOs(skuRepository.findOnSaleBySpuId(spuId));
    }

    /** 商品全部 SKU 视图（详情页含下架）。 */
    public List<SkuVO> listBySpu(Long spuId) {
        return skuConverter.toVOs(skuRepository.findBySpuId(spuId));
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
    public Map<Long, Integer> getMinPriceMapBySpuIds(List<Long> spuIds) {
        return skuRepository.findOnSaleBySpuIds(spuIds).stream()
                .filter(sku -> sku.getPrice() != null)
                .collect(Collectors.toMap(
                        Sku::getSpuId,
                        Sku::getPrice,
                        Math::min));
    }

    // ==================== 私有方法 ====================

    /**
     * 回填商品展示四字段 —— 去重后的 SKU ID 集合<b>一次</b>联表查询（ADR-057）。
     *
     * <p>fail-open：查询异常 → 记 warn 后原样返回（四字段保持 null）；商品行缺失
     * （脏数据/物理删除）→ 该 SKU 的四字段保持 null。两种情况都<b>不</b>抛异常，
     * 也不影响 SKU 本体字段 —— 购物车与下单取数不因展示字段缺失而变脆。
     */
    private void enrichSpuDisplay(List<SkuCO> cos) {
        if (cos == null || cos.isEmpty()) {
            return;
        }
        List<Long> skuIds = cos.stream()
                .map(SkuCO::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (skuIds.isEmpty()) {
            return;
        }
        Map<Long, SkuSpuDisplay> displays;
        try {
            displays = skuSpuDisplayQuery.findBySkuIds(skuIds);
        } catch (Exception e) {
            log.warn("SKU 商品展示字段回填失败，四字段置空（fail-open）: skuIds={}, cause={}",
                    skuIds, e.getMessage());
            return;
        }
        if (displays == null || displays.isEmpty()) {
            return;
        }
        for (SkuCO co : cos) {
            SkuSpuDisplay display = displays.get(co.getId());
            if (display == null) {
                continue;
            }
            co.setProductName(display.productName());
            co.setSellerId(display.sellerId());
            co.setCategoryId(display.categoryId());
            co.setFreightTemplateId(display.freightTemplateId());
        }
    }
}
