package org.icedamericanomall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.icedamericanomall.convert.CartConverter;
import org.icedamericanomall.dto.request.CartAddReq;
import org.icedamericanomall.dto.request.CartUpdateReq;
import org.icedamericanomall.dto.response.CartItemResp;
import org.icedamericanomall.dto.response.CartVO;
import org.icedamericanomall.mapper.CartMapper;
import org.icedamericanomall.pojo.CartEntity;
import org.icedamericanomall.service.ICartService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CartServiceImpl extends ServiceImpl<CartMapper, CartEntity> implements ICartService {

    private final CartConverter cartConverter;

    public CartServiceImpl(CartConverter cartConverter) {
        this.cartConverter = cartConverter;
    }

    /**
     * <pre>
     * Scenario: 查看购物车
     *   Given 用户已登录
     *   When GET /api/cart
     *   Then 返回购物车列表（含商品名/规格/单价/数量/选中状态/小计）
     *   And 计算总价和选中商品总价
     * </pre>
     */
    @Override
    public CartVO getCart(Long userId) {
        List<CartEntity> items = lambdaQuery().eq(CartEntity::getUserId, userId)
                .orderByDesc(CartEntity::getCreateTime).list();
        return buildCartVO(items);
    }

    @Override
    public List<CartEntity> getSelectedItems(Long userId) {
        return lambdaQuery()
                .eq(CartEntity::getUserId, userId)
                .eq(CartEntity::getSelected, true)
                .list();
    }

    /**
     * <pre>
     * Scenario: 添加商品到购物车（去重合并）
     *   Given 用户已登录
     *   When POST /api/cart/item with {skuId, quantity}
     *   Then 若购物车中不存在同一 SKU，则新增记录
     *   And 若已存在同一 SKU，则累加数量（防重复）
     *   And 默认设置为选中状态
     * </pre>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addItem(Long userId, CartAddReq req) {
        // (user_id, sku_id) 唯一约束 —— 相同SKU累加数量，不创建重复行
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

    /**
     * <pre>
     * Scenario: 修改购物车商品数量
     *   Given 购物车中存在该 SKU
     *   When PUT /api/cart/item with {skuId, quantity}
     *   Then 更新该购物车项的数量
     *
     * Scenario: 数量设为0则删除
     *   Given 购物车中存在该 SKU
     *   When 数量 ≤ 0
     *   Then 该购物车项被删除
     * </pre>
     */
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

    /**
     * V4.0: 删除指定购物车项（仅删除已下单的商品，京东标准）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(Long userId, List<Long> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) return;
        lambdaUpdate()
                .eq(CartEntity::getUserId, userId)
                .in(CartEntity::getId, cartItemIds)
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
