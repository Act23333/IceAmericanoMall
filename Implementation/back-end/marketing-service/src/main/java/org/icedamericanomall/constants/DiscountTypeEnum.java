package org.icedamericanomall.constants;

import lombok.Getter;

/** V4.0: 优惠计算类型枚举（京东标准：满减/折扣/代金券三分类） */
@Getter
public enum DiscountTypeEnum {
    FIXED(1, "满减券"),
    PERCENTAGE(2, "折扣券"),
    CASH_COUPON(3, "代金券");

    private final int code;
    private final String desc;

    DiscountTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static String descOf(int code) {
        for (DiscountTypeEnum e : values()) {
            if (e.code == code) return e.desc;
        }
        return "未知";
    }
}
