package org.icedamericanomall.marketing.domain.enums;

import lombok.Getter;

/**
 * 订单类型枚举（营销侧口径）—— 与 trade-service {@code OrderTypeEnum} 的码值保持一致，
 * 用于优惠券适用范围校验（如秒杀订单不接受任何优惠券）。
 */
@Getter
public enum OrderTypeEnum {
    NORMAL(1, "购物车下单"),
    DIRECT(2, "立即购买"),
    FLASH_SALE(3, "秒杀订单");

    private final int code;
    private final String desc;

    OrderTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
