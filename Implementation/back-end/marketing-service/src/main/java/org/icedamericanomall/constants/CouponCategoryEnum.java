package org.icedamericanomall.constants;

import lombok.Getter;

/** V4.0: 优惠券类别枚举（京东标准：平台/店铺/秒杀/独占四分类） */
@Getter
public enum CouponCategoryEnum {
    PLATFORM(1, "平台券"),
    SHOP(2, "店铺券"),
    FLASH_SALE(3, "秒杀券"),
    EXCLUSIVE(4, "独占券");

    private final int code;
    private final String desc;

    CouponCategoryEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
