package org.icedamericanomall.cart.adapter.internal;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.cart.app.CartManager;
import org.icedamericanomall.dto.CartItemCO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Internal cart endpoints for inter-service Feign calls.
 * All endpoints return raw types without Result wrapping (internal API convention).
 */
@RestController
@RequestMapping("/internal/cart")
@RequiredArgsConstructor
public class InternalCartController {

    private final CartManager cartManager;

    /**
     * Get selected cart items for a user (for order creation).
     */
    @GetMapping("/selected")
    public List<CartItemCO> getSelectedItems(@RequestParam Long userId) {
        return cartManager.getSelectedItemCos(userId);
    }

    /**
     * Clear all cart items for a user (after successful order).
     */
    @DeleteMapping("/clear")
    public void clearCart(@RequestParam Long userId) {
        cartManager.clearCart(userId);
    }

    /**
     * V4.0: Delete specific cart items after successful order (only ordered items, not entire cart).
     */
    @DeleteMapping("/items")
    public void deleteByIds(@RequestParam Long userId, @RequestBody List<Long> cartItemIds) {
        cartManager.deleteByIds(userId, cartItemIds);
    }
}
