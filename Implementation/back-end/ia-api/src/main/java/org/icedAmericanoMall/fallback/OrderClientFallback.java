package org.icedAmericanoMall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.client.OrderClient;
import org.icedAmericanoMall.dto.OrderSummaryDTO;
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
        };
    }
}
