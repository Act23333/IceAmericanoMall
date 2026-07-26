package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.CartClient;
import org.icedamericanomall.dto.CartItemDTO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class CartClientFallback implements FallbackFactory<CartClient> {
    @Override
    public CartClient create(Throwable cause) {
        return new CartClient() {
            @Override
            public List<CartItemDTO> getSelectedItems(Long userId) {
                log.error("获取选中购物车项失败, userId={}", userId, cause);
                return List.of();
            }

            @Override
            public void clearCart(Long userId) {
                log.error("清空购物车失败, userId={}", userId, cause);
            }
        };
    }
}
