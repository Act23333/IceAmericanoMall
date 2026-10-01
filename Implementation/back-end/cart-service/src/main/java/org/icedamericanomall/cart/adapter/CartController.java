package org.icedamericanomall.cart.adapter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.cart.dto.request.CartAddReq;
import org.icedamericanomall.cart.dto.request.CartUpdateReq;
import org.icedamericanomall.cart.dto.response.CartVO;
import org.icedamericanomall.cart.service.ICartService;
import org.icedamericanomall.common.domain.Result;
import org.icedamericanomall.common.domain.UserContext;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequestMapping("/api/cart")
@RestController
@RequiredArgsConstructor
@Validated
public class CartController {

    private final ICartService cartService;

    @GetMapping
    public Result<CartVO> getCart() {
        Long userId = UserContext.getUserId();
        CartVO cartVO = cartService.getCart(userId);
        return Result.ok(cartVO);
    }

    @PostMapping("/item")
    public Result<Void> addItem(@Valid @RequestBody CartAddReq req) {
        Long userId = UserContext.getUserId();
        cartService.addItem(userId, req);
        return Result.ok();
    }

    @PutMapping("/item")
    public Result<Void> updateItem(@Valid @RequestBody CartUpdateReq req) {
        Long userId = UserContext.getUserId();
        cartService.updateItem(userId, req);
        return Result.ok();
    }

    @DeleteMapping("/item/{skuId}")
    public Result<Void> removeItem(@PathVariable @NotNull(message = "SKU ID不能为空") Long skuId) {
        Long userId = UserContext.getUserId();
        cartService.removeItem(userId, skuId);
        return Result.ok();
    }

    @PatchMapping("/item/{skuId}/selected")
    public Result<Void> selectItem(@PathVariable Long skuId,
                                   @RequestParam @NotNull Boolean selected) {
        Long userId = UserContext.getUserId();
        cartService.selectItem(userId, skuId, selected);
        return Result.ok();
    }

    @DeleteMapping("/clear")
    public Result<Void> clearCart() {
        Long userId = UserContext.getUserId();
        cartService.clearCart(userId);
        return Result.ok();
    }
}
