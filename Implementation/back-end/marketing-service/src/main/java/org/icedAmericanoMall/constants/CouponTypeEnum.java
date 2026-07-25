package org.icedAmericanoMall.constants;

import lombok.Getter;

/** 大厂标准: 优惠券类型枚举（京东/阿里优惠券类型体系） */
@Getter
public enum CouponTypeEnum {
    FIXED(1, "满减券"),
    PERCENTAGE(2, "折扣券");

    private final int code;
    private final String desc;

    CouponTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
