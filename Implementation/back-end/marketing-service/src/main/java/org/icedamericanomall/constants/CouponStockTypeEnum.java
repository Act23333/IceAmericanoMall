package org.icedamericanomall.constants;

import lombok.Getter;

/** V4.0: 优惠券总量模型枚举（京东标准：限量/不限量） */
@Getter
public enum CouponStockTypeEnum {
    LIMITED(1, "有总量上限"),
    UNLIMITED(2, "无限量");

    private final int code;
    private final String desc;

    CouponStockTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
