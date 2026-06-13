package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.icedAmericanoMall.convert.CartConverter;
import org.icedAmericanoMall.dto.request.CartAddReq;
import org.icedAmericanoMall.dto.request.CartUpdateReq;
import org.icedAmericanoMall.dto.response.CartItemResp;
import org.icedAmericanoMall.dto.response.CartVO;
import org.icedAmericanoMall.mapper.CartMapper;
import org.icedAmericanoMall.pojo.CartEntity;
import org.icedAmericanoMall.service.ICartService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CartServiceImpl extends ServiceImpl<CartMapper, CartEntity> implements ICartService {

    private final CartConverter cartConverter;

    public CartServiceImpl(CartConverter cartConverter) {
        this.cartConverter = cartConverter;
    }

    @Override
    public CartVO getCart(Long userId) {
        List<CartEntity> items = lambdaQuery().eq(CartEntity::getUserId, userId).list();
        // SKU enrichment happens in CartManager — here we just return raw cart data
        return buildCartVO(items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addItem(Long userId, CartAddReq req) {
        // Upsert: if item with same userId+skuId exists, increment quantity
        CartEntity existing = lambdaQuery()
                .eq(CartEntity::getUserId, userId)
                .eq(CartEntity::getSkuId, req.getSkuId())
                .one();
        if (existing != null) {
            lambdaUpdate()
                    .eq(CartEntity::getId, existing.getId())
                    .setIncrBy(CartEntity::getQuantity, req.getQuantity())
                    .update();
            return;
        }
        CartEntity cartEntity = new CartEntity();
        cartEntity.setUserId(userId);
        cartEntity.setSkuId(req.getSkuId());
        cartEntity.setQuantity(req.getQuantity());
        cartEntity.setSelected(true);
        save(cartEntity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateItem(Long userId, CartUpdateReq req) {
        CartEntity existing = lambdaQuery()
                .eq(CartEntity::getUserId, userId)
                .eq(CartEntity::getSkuId, req.getSkuId())
                .one();
        if (existing == null) {
            return;
        }
        if (req.getQuantity() <= 0) {
            removeById(existing.getId());
            return;
        }
        lambdaUpdate()
                .eq(CartEntity::getId, existing.getId())
                .set(CartEntity::getQuantity, req.getQuantity())
                .update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeItem(Long userId, Long skuId) {
        lambdaUpdate()
                .eq(CartEntity::getUserId, userId)
                .eq(CartEntity::getSkuId, skuId)
                .remove();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void selectItem(Long userId, Long skuId, Boolean selected) {
        lambdaUpdate()
                .eq(CartEntity::getUserId, userId)
                .eq(CartEntity::getSkuId, skuId)
                .set(CartEntity::getSelected, selected)
                .update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearCart(Long userId) {
        lambdaUpdate()
                .eq(CartEntity::getUserId, userId)
                .remove();
    }

    private CartVO buildCartVO(List<CartEntity> items) {
        CartVO vo = new CartVO();
        List<CartItemResp> respItems = cartConverter.entitiesToResps(items);
        vo.setItems(respItems);
        int totalPrice = 0;
        int selectedPrice = 0;
        boolean allSelected = !respItems.isEmpty();
        for (CartItemResp item : respItems) {
            if (item.getPrice() != null) {
                totalPrice += item.getSubTotal();
                if (Boolean.TRUE.equals(item.getSelected())) {
                    selectedPrice += item.getSubTotal();
                } else {
                    allSelected = false;
                }
            }
        }
        vo.setTotalPrice(totalPrice);
        vo.setSelectedPrice(selectedPrice);
        vo.setAllSelected(allSelected);
        return vo;
    }
}
