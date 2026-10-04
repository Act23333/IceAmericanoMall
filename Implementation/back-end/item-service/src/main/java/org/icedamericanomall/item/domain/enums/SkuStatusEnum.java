package org.icedamericanomall.item.domain.enums;

import lombok.Getter;

/**
 * SKU 状态（{@code sku.status}）—— 领域枚举，零框架依赖。
 *
 * <p>数据库口径不可变：{@code 1=可售}、{@code 0=停售}（原子扣减 SQL 的
 * {@code WHERE status = 1} 与之对齐，ADR-020）。
 */
@Getter
public enum SkuStatusEnum {

    ON_SALE(1, "可售"),
    OFF_SALE(0, "停售");

    private final int code;
    private final String desc;

    SkuStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按状态码解析枚举。
     *
     * @param code 状态码（可空）
     * @return 匹配的枚举；不存在（含 null）返回 {@code null}
     */
    public static SkuStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (SkuStatusEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
