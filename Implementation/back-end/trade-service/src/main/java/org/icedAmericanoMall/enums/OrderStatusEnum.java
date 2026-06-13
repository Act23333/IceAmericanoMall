package org.icedAmericanoMall.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum OrderStatusEnum {
    PENDING_PAYMENT(1, "待付款"),
    PENDING_SHIPMENT(2, "待发货"),
    PENDING_RECEIPT(3, "待收货"),
    COMPLETED(4, "已完成"),
    CANCELLED(5, "已取消"),
    PENDING_REVIEW(6, "待评价");

    @EnumValue
    private final int code;
    private final String desc;

    OrderStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
