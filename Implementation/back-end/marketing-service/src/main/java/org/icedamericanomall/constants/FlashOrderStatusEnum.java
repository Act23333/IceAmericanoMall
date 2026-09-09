package org.icedamericanomall.constants;

import lombok.Getter;

/**
 * V4.1: 标记废弃。秒杀订单状态统一使用 trade-service 的 OrderStatusEnum。
 *
 * @deprecated 请使用 trade-service 的 OrderStatusEnum。
 */
@Deprecated
@Getter
public enum FlashOrderStatusEnum {
    PENDING(0, "pending"),
    CREATED(1, "created"),
    FAILED(2, "failed");

    private final int code;
    private final String desc;

    FlashOrderStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
