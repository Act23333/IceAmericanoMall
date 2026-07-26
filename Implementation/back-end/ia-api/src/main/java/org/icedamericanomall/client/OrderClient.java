package org.icedamericanomall.client;

import org.icedamericanomall.dto.OrderSummaryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for trade-service internal order endpoints.
 * Used by pay-service to update order status and query order amount.
 */
@FeignClient(name = "trade-service", path = "/internal/trade/order")
public interface OrderClient {

    /**
     * Update order status after payment success / timeout.
     */
    @PutMapping("/{orderNo}/status")
    void updateOrderStatus(@PathVariable String orderNo, @RequestParam Integer status);

    /**
     * Get order summary (for payment amount retrieval).
     */
    @GetMapping("/{orderNo}")
    OrderSummaryDTO getOrder(@PathVariable String orderNo);
}
