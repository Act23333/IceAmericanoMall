package org.noLazy.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OrderCreatedEvent extends DomainEvent {
    private static final long serialVersionUID = 1L;

    /** 订单号 */
    private String orderNo;
    /** 买家ID */
    private Long userId;
    /** 实付金额(分) */
    private Integer payAmount;
    /** 支付渠道: WECHAT/ALIPAY/BALANCE */
    private String payChannel;
}
