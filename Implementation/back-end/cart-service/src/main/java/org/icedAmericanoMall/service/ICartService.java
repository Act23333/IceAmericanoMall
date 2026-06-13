package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.icedAmericanoMall.dto.request.CartAddReq;
import org.icedAmericanoMall.dto.request.CartUpdateReq;
import org.icedAmericanoMall.dto.response.CartVO;
import org.icedAmericanoMall.pojo.CartEntity;

public interface ICartService extends IService<CartEntity> {
    CartVO getCart(Long userId);

    void addItem(Long userId, @Valid CartAddReq req);

    void updateItem(Long userId, @Valid CartUpdateReq req);

    void removeItem(Long userId, Long skuId);

    void selectItem(Long userId, Long skuId, @NotNull Boolean selected);

    void clearCart(Long userId);
}
