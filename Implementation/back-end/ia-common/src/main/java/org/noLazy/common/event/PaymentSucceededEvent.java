package org.noLazy.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 支付成功事件 — pay-service 发布。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PaymentSucceededEvent extends DomainEvent {
    private static final long serialVersionUID = 1L;

    private String payOrderNo;
    private String orderNo;
    /** 金额（分） */
    private Integer amount;
}
