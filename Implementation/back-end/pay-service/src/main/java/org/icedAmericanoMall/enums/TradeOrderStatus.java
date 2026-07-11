package org.icedAmericanoMall.enums;

import lombok.Getter;

/**
 * trade-service 订单状态码 —— pay-service 通过 Feign 更新订单状态时引用的跨服务契约值。
 * 与 trade-service 的 OrderStatusEnum 对应，避免 pay-service 中散落魔法值 2/5。
 */
@Getter
public enum TradeOrderStatus {
    PENDING_SHIPMENT(2, "待发货"),
    CANCELLED(5, "已取消");

    private final int code;
    private final String desc;

    TradeOrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
