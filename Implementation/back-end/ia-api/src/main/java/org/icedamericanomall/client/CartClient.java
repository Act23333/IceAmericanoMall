package org.icedamericanomall.client;

import org.icedamericanomall.dto.CartItemCO;
import org.icedamericanomall.fallback.CartClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign client for cart-service internal endpoints.
 * Used by trade-service to fetch selected items and clear cart after order.
 *
 * <p>降级语义：<b>全部方法 fail-closed</b>（抛 {@code BizException}），
 * 因 {@code getSelectedItems} 的空列表会被调用方误读为「购物车为空」，
 * 而 {@code clearCart}/{@code deleteByIds} 静默失败会导致重复下单。
 * 详见 {@link CartClientFallback} 与 {@code org.icedamericanomall.fallback} 包规范。
 */
@FeignClient(name = "cart-service", path = "/internal/cart", contextId = "cart",
        fallbackFactory = CartClientFallback.class)
public interface CartClient {

    /**
     * Get selected cart items for order creation.
     */
    @GetMapping("/selected")
    List<CartItemCO> getSelectedItems(@RequestParam(name = "user_id") Long userId);

    /**
     * Clear cart after successful order.
     */
    @DeleteMapping("/clear")
    void clearCart(@RequestParam(name = "user_id") Long userId);

    /**
     * V4.0: Delete specific cart items by their IDs (only ordered items, not entire cart).
     */
    @DeleteMapping("/items")
    void deleteByIds(@RequestParam(name = "user_id") Long userId, @RequestBody List<Long> cartItemIds);
}
