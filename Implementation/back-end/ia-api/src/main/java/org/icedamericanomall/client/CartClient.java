package org.icedamericanomall.client;

import org.icedamericanomall.dto.CartItemDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign client for cart-service internal endpoints.
 * Used by trade-service to fetch selected items and clear cart after order.
 */
@FeignClient(name = "cart-service", path = "/internal/cart")
public interface CartClient {

    /**
     * Get selected cart items for order creation.
     */
    @GetMapping("/selected")
    List<CartItemDTO> getSelectedItems(@RequestParam Long userId);

    /**
     * Clear cart after successful order.
     */
    @DeleteMapping("/clear")
    void clearCart(@RequestParam Long userId);

    /**
     * V4.0: Delete specific cart items by their IDs (only ordered items, not entire cart).
     */
    @DeleteMapping("/items")
    void deleteByIds(@RequestParam Long userId, @RequestBody List<Long> cartItemIds);
}
