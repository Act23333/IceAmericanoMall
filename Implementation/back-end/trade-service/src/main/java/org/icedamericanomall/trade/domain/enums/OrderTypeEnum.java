package org.icedamericanomall.trade.domain.enums;

import lombok.Getter;

/**
 * V4.1: 订单类型枚举（京东/淘宝统一订单中心标准）。
 * 所有订单类型对应的创建策略由 OrderCreateStrategyFactory 路由。
 *
 * <p><b>注意 PRESALE(4)</b>：预售<b>尚未实现</b> —— 没有任何 {@code OrderCreateStrategy}
 * 声明 {@code supportedType() == PRESALE}，因此以该类型下单会在
 * {@code OrderCreateStrategyFactory.getStrategy} 处直接抛出
 * {@code BizException(PARAM_ERROR, "不支持的订单类型: PRESALE")}。
 * 枚举值保留（订单表历史数据与前端类型位不可回收），如需上线预售须先补策略实现。
 */
@Getter
public enum OrderTypeEnum {
    NORMAL(1, "购物车下单"),
    DIRECT(2, "立即购买"),
    FLASH_SALE(3, "秒杀订单"),
    /** 预售未实现，下单会 PARAM_ERROR（见类注释）。 */
    PRESALE(4, "预售订单"),
    /** F13 拼团订单（V5.14, ADR-049）：两段式参团 —— 下单不占团位，支付成功后经 I53 自动入团。 */
    GROUP_BUY(5, "拼团订单");

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
