package org.icedamericanomall.manager;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.dto.request.CartAddReq;
import org.icedamericanomall.dto.request.CartUpdateReq;
import org.icedamericanomall.dto.response.CartVO;
import org.icedamericanomall.pojo.CartEntity;
import org.icedamericanomall.service.ICartService;
import org.noLazy.common.utils.UserContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * V4.0 DDD: 购物车编排 Manager（Application层）。
 * 职责: 购物车CRUD编排 + 用户上下文提取（Controller不处理userId）。
 */
@Component
@RequiredArgsConstructor
public class CartManager {

    private final ICartService cartService;

    public CartVO getCart() {
        return cartService.getCart(UserContext.getUserId());
    }

    public List<CartEntity> getSelectedItems() {
        return cartService.getSelectedItems(UserContext.getUserId());
    }

    public void addItem(@Valid CartAddReq req) {
        cartService.addItem(UserContext.getUserId(), req);
    }

    public void updateItem(@Valid CartUpdateReq req) {
        cartService.updateItem(UserContext.getUserId(), req);
    }

    public void removeItem(Long skuId) {
        cartService.removeItem(UserContext.getUserId(), skuId);
    }

    public void selectItem(Long skuId, @NotNull Boolean selected) {
        cartService.selectItem(UserContext.getUserId(), skuId, selected);
    }

    public void clearCart() {
        cartService.clearCart(UserContext.getUserId());
    }
}
