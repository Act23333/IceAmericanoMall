package org.noLazy.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 订单创建事件 — trade-service 发布，pay-service 消费自动创建支付单。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderCreatedEvent extends DomainEvent {
    @Serial
    private static final long serialVersionUID = 1L;

    private String orderNo;
    private Long userId;
    /** 金额（分） */
    private Integer totalAmount;
}
