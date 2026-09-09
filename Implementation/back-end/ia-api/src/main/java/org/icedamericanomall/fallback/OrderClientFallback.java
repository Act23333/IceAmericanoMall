package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.OrderClient;
import org.icedamericanomall.dto.CreateOrderInternalReq;
import org.icedamericanomall.dto.OrderSummaryDTO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderClientFallback implements FallbackFactory<OrderClient> {
    @Override
    public OrderClient create(Throwable cause) {
        return new OrderClient() {
            @Override
            public void updateOrderStatus(String orderNo, Integer status) {
                log.error("更新订单状态失败, orderNo={}, status={}", orderNo, status, cause);
            }

            @Override
            public OrderSummaryDTO getOrder(String orderNo) {
                log.error("获取订单失败, orderNo={}", orderNo, cause);
                return null;
            }

            @Override
            public OrderSummaryDTO createOrder(CreateOrderInternalReq req) {
                log.error("创建秒杀订单失败, userId={}, flashId={}", req.getUserId(), req.getFlashId(), cause);
                return null;
            }
        };
    }
}
