package org.icedamericanomall.trade.domain.service;

import org.icedamericanomall.trade.domain.enums.StockTypeEnum;

/**
 * 库存可售性判定（领域服务，纯函数）—— 缺货提示与下单校验各自的库存口径。
 *
 * <p><b>为何需要本类</b>：两种语义必须分开，历史上常被混用：
 * <ul>
 *   <li><b>下单校验</b>（fail-closed，{@link StockTypeEnum#requiresStockCheck}）：
 *       「{@code null} 或 {@code LIMITED} 才看库存上限」—— 保留为现有下单链路语义，
 *       本次<b>不改动</b>（改它会影响下单成败）。</li>
 *   <li><b>缺货展示提示</b>（fail-open，{@link #requiresStockCheck}）：
 *       {@code LIMITED}/{@code PRESALE} 属「需校验库存」类型；
 *       {@code UNLIMITED 不限量} 库存恒为 0 <b>不是</b>缺货。
 *       —— ADR-061 §决策 3 的比对语义表明确要求这一口径。</li>
 * </ul>
 *
 * <p>本类是纯函数、零框架依赖，可直接单测（含「去掉 {@code stockType} 判定 → 不限量误报缺货」
 * 的变异验证）。
 */
public final class StockAvailabilityPolicy {

    private StockAvailabilityPolicy() {
    }

    /**
     * 该库存类型是否「需校验库存」（用于缺货提示）。
     *
     * <p>口径：{@code LIMITED(1)} 与 {@code PRESALE(3)} 需校验；{@code UNLIMITED(2)} 不需；
     * {@code null}（历史数据缺省）按 {@code LIMITED} 处理。
     *
     * @param stockType 库存类型码（{@code sku.stock_type}），可空
     * @return true = 库存为 0 时应提示缺货
     */
    public static boolean requiresStockCheck(Integer stockType) {
        if (stockType == null) {
            return true;   // 历史数据缺省语义 = 有限量
        }
        return stockType == StockTypeEnum.LIMITED.getCode()
                || stockType == StockTypeEnum.PRESALE.getCode();
    }
}
