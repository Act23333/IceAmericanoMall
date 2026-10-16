package org.icedamericanomall.trade.app.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.trade.app.port.OrderItemCurrentStateQuery;
import org.icedamericanomall.trade.domain.model.order.OrderItem;
import org.icedamericanomall.trade.domain.model.order.SkuCurrentState;
import org.icedamericanomall.trade.domain.model.order.SpuCurrentState;
import org.icedamericanomall.trade.domain.service.OrderItemPriceComparator;
import org.icedamericanomall.trade.domain.service.OrderItemPriceComparator.CompareResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 订单项比价/失效提示增强（应用层读时增强 —— ADR-061 §决策 3 / ADR-057）。
 *
 * <p><b>这不是订单域事实</b>：不写订单、不改金额、不改状态机；输出<b>仅用于展示</b>。
 *
 * <p><b>三条硬口径</b>：
 * <ol>
 *   <li><b>按业务键批量</b>：整页一次去重后查询（SKU 一次 + SPU 一次），<b>禁止逐行</b>，无 N+1；</li>
 *   <li><b>fail-open</b>：数据源异常 → 空 Map + WARN，<b>不抛</b>；端口未接线/未启用 → 空 Map；</li>
 *   <li><b>不缩行</b>：任何情况下返回的结果条数 = 入参明细条数（缺行 → {@code UNCOMPARABLE}）
 *       —— 读时增强的失败绝不能让订单列表/详情少一条。</li>
 * </ol>
 *
 * <p><b>事务边界（ADR-057 第三条）</b>：本类只调用同库只读实现，故可被
 * {@code @Transactional} 的 Manager 调用；若将来改为 Feign，调用方必须去掉事务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCurrentStateEnricher {

    private final OrderItemCurrentStateQuery orderItemCurrentStateQuery;

    /**
     * 为一批订单项产出比对结果（fail-open，逐条对齐入参顺序）。
     *
     * @param items 订单项快照（可空/空 → 返回空 Map）
     * @return 订单项 ID → 比对结果；<b>每条非空 ID 的入参明细必有结果</b>（缺数据 → {@code UNCOMPARABLE}）
     */
    public Map<Long, CompareResult> compare(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }
        Map<String, SkuCurrentState> skuStates = loadSkuStates(items);
        Map<String, SpuCurrentState> spuStates = loadSpuStates(skuStates);
        Map<Long, CompareResult> results = new LinkedHashMap<>();
        for (OrderItem item : items) {
            if (item.getId() == null) {
                continue;
            }
            SkuCurrentState skuState = item.getSkuId() == null ? null : skuStates.get(item.getSkuId());
            String spuKey = skuState != null && skuState.spuId() != null ? skuState.spuId() : item.getSpuId();
            SpuCurrentState spuState = spuKey == null ? null : spuStates.get(spuKey);
            results.put(item.getId(), OrderItemPriceComparator.compare(
                    item.getPrice(), item.getSpuId(), skuState, spuState));
        }
        return results;
    }

    /** SKU 当前状态（批量；异常 → 空 Map + WARN，fail-open）。 */
    private Map<String, SkuCurrentState> loadSkuStates(List<OrderItem> items) {
        List<String> skuIds = items.stream()
                .map(OrderItem::getSkuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        try {
            Map<String, SkuCurrentState> states = orderItemCurrentStateQuery.findSkuStates(skuIds);
            return states == null ? Map.of() : states;
        } catch (Exception e) {
            log.warn("订单项实时状态读取失败，比价态全部降级为「无法比对」（fail-open，不缩行）: "
                    + "skuCount={}, cause={}", skuIds.size(), e.getMessage());
            return Map.of();
        }
    }

    /** SPU 当前状态（批量；键取自 SKU 行现属 SPU，异常 → 空 Map + WARN，fail-open）。 */
    private Map<String, SpuCurrentState> loadSpuStates(Map<String, SkuCurrentState> skuStates) {
        List<String> spuIds = skuStates.values().stream()
                .map(SkuCurrentState::spuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (spuIds.isEmpty()) {
            return Map.of();
        }
        try {
            Map<String, SpuCurrentState> states = orderItemCurrentStateQuery.findSpuStates(spuIds);
            return states == null ? Map.of() : states;
        } catch (Exception e) {
            // 上下架提示降级为「不可判定」——其余比对仍可给出（价格/缺货），故不整体放弃 SKU 侧结果
            log.warn("商品上下架状态读取失败，上下架提示降级（fail-open，不缩行）: spuCount={}, cause={}",
                    spuIds.size(), e.getMessage());
            return Map.of();
        }
    }
}
