package org.icedamericanomall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.icedamericanomall.dto.request.CartAddReq;
import org.icedamericanomall.dto.request.CartUpdateReq;
import org.icedamericanomall.dto.response.CartVO;
import org.icedamericanomall.pojo.CartEntity;

import java.util.List;

public interface ICartService extends IService<CartEntity> {
    CartVO getCart(Long userId);

    /**
     * Get selected cart items (raw entities) for order creation — internal Feign use.
     */
    List<CartEntity> getSelectedItems(Long userId);

    void addItem(Long userId, @Valid CartAddReq req);

    void updateItem(Long userId, @Valid CartUpdateReq req);

    void removeItem(Long userId, Long skuId);

    void selectItem(Long userId, Long skuId, @NotNull Boolean selected);

    void clearCart(Long userId);
}
