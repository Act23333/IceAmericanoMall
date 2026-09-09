package org.icedamericanomall.controller;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.dto.CartItemDTO;
import org.icedamericanomall.pojo.CartEntity;
import org.icedamericanomall.service.ICartService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

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
    public List<CartItemDTO> getSelectedItems(@RequestParam Long userId) {
        List<CartEntity> items = cartService.getSelectedItems(userId);
        return items.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * Clear all cart items for a user (after successful order).
     */
    @DeleteMapping("/clear")
    public void clearCart(@RequestParam Long userId) {
        cartService.clearCart(userId);
    }

    /**
     * V4.0: Delete specific cart items after successful order (only ordered items, not entire cart).
     */
    @DeleteMapping("/items")
    public void deleteByIds(@RequestParam Long userId, @RequestBody List<Long> cartItemIds) {
        cartService.deleteByIds(userId, cartItemIds);
    }

    private CartItemDTO toDTO(CartEntity entity) {
        CartItemDTO dto = new CartItemDTO();
        dto.setCartItemId(entity.getId());
        dto.setSkuId(entity.getSkuId());
        dto.setProductId(entity.getProductId());
        dto.setProductName(entity.getProductName());
        dto.setSpec(entity.getSkuSpec());
        dto.setImage(entity.getImage());
        dto.setPrice(entity.getPrice());
        dto.setQuantity(entity.getQuantity());
        return dto;
    }
}
