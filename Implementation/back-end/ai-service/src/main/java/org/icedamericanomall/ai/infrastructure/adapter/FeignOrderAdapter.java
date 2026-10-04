package org.icedamericanomall.ai.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.ai.domain.model.order.OrderBrief;
import org.icedamericanomall.ai.domain.port.OrderPort;
import org.icedamericanomall.client.OrderClient;
import org.icedamericanomall.dto.OrderSummaryCO;
import org.springframework.stereotype.Component;

/**
 * {@link OrderPort} 的 Feign 实现（防腐层）——把 trade-service 的订单 CO 转成本上下文领域对象。
 */
@Component
@RequiredArgsConstructor
public class FeignOrderAdapter implements OrderPort {

    private final OrderClient orderClient;

    @Override
    public OrderBrief getOrder(String orderNo) {
        OrderSummaryCO order = orderClient.getOrder(orderNo);
        if (order == null) {
            return null;
        }
        return new OrderBrief(order.getOrderNo(), order.getStatus(), order.getTotalAmount());
    }
}
