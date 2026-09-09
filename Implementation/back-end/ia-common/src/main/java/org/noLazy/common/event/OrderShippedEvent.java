package org.noLazy.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单发货事件 — trade-service 发布，logistics-service 消费自动创建物流记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderShippedEvent extends DomainEvent {
    private static final long serialVersionUID = 1L;

    private Long orderId;
    private String orderNo;
    private String logisticsNumber;
    private String logisticsCompany;
    private String contact;
    private String mobile;
}
