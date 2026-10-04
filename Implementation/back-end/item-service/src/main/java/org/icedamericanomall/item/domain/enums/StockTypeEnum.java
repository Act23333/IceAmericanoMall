package org.icedamericanomall.item.domain.enums;

import lombok.Getter;

/**
 * 库存类型（{@code sku.stock_type}）—— 领域枚举，零框架依赖。
 *
 * <p>{@link #UNLIMITED} 的 SKU 不校验库存，扣减仅累加销量（见 {@code SkuStockPolicy}）。
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
     * 按类型码解析枚举。
     *
     * @param code 类型码（可空）
     * @return 匹配的枚举；不存在（含 null）返回 {@code null}
     */
    public static StockTypeEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (StockTypeEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
