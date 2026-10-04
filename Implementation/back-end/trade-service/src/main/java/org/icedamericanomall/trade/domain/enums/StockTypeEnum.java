package org.icedamericanomall.trade.domain.enums;

import lombok.Getter;

/**
 * 库存类型枚举 —— 与 item-service 的 {@code StockTypeEnum} 对齐（V4.0 京东标准：限量/不限量/预售）。
 * <p>
 * 下单校验只对「限量」类型检查可售库存；「不限量」与「预售」不校验库存上限。
 */
@Getter
public enum StockTypeEnum {

    LIMITED(1, "有限量"),
    UNLIMITED(2, "不限量"),
    PRESALE(3, "预售");

    private final int code;
    private final String desc;

    StockTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 是否需要校验可售库存。
     *
     * @param stockType SKU 库存类型码，{@code null} 视为「限量」（历史数据缺省语义）
     * @return true=需要校验库存上限
     */
    public static boolean requiresStockCheck(Integer stockType) {
        return stockType == null || stockType == LIMITED.code;
    }
}
