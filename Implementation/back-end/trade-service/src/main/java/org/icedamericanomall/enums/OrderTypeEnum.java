package org.icedamericanomall.enums;

import lombok.Getter;

/**
 * V4.1: 订单类型枚举（京东/淘宝统一订单中心标准）。
 * 所有订单类型对应的创建策略由 OrderCreateStrategyFactory 路由。
 */
@Getter
public enum OrderTypeEnum {
    NORMAL(1, "购物车下单"),
    DIRECT(2, "立即购买"),
    FLASH_SALE(3, "秒杀订单"),
    PRESALE(4, "预售订单");

    private final int code;
    private final String desc;

    OrderTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static OrderTypeEnum of(int code) {
        for (OrderTypeEnum e : values()) {
            if (e.code == code) return e;
        }
        return NORMAL;
    }
}
