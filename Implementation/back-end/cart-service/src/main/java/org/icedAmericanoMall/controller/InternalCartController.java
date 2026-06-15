package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.pojo.CartEntity;
import org.icedAmericanoMall.service.ICartService;
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

    private final ICartService cartService;

    /**
     * Get selected cart items for a user (for order creation).
     */
    @GetMapping("/selected")
    public List<CartEntity> getSelectedItems(@RequestParam Long userId) {
        return cartService.getSelectedItems(userId);
    }

    /**
     * Clear all cart items for a user (after successful order).
     */
    @DeleteMapping("/clear")
    public void clearCart(@RequestParam Long userId) {
        cartService.clearCart(userId);
    }
}
