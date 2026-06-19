package org.noLazy.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单状态变更事件 — 推送 WebSocket 通知。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderStatusChangedEvent extends DomainEvent {
    private static final long serialVersionUID = 1L;
    private String orderNo;
    private Long userId;
    private Integer oldStatus;
    private Integer newStatus;
    private String message;
}
