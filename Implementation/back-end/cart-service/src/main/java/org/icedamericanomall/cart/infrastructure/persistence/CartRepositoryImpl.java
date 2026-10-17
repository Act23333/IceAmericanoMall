package org.icedamericanomall.cart.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.cart.domain.model.cart.CartItem;
import org.icedamericanomall.cart.domain.repository.CartRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 购物车仓储实现 —— MyBatis-Plus 访问 {@code cart} 表，PO ↔ 聚合转换内聚于此。
 */
@Repository
@RequiredArgsConstructor
public class CartRepositoryImpl implements CartRepository {

    private final CartMapper cartMapper;

    @Override
    public List<CartItem> findByUserIdOrderByCreateTimeDesc(Long userId) {
        return cartMapper.selectList(new LambdaQueryWrapper<CartPO>()
                        .eq(CartPO::getUserId, userId)
                        .orderByDesc(CartPO::getCreateTime))
                .stream()
                .map(CartRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public long countByUserId(Long userId) {
        Long count = cartMapper.selectCount(new LambdaQueryWrapper<CartPO>()
                .eq(CartPO::getUserId, userId));
        return count == null ? 0L : count;
    }

    @Override
    public List<CartItem> findSelectedByUserId(Long userId) {
        return cartMapper.selectList(new LambdaQueryWrapper<CartPO>()
                        .eq(CartPO::getUserId, userId)
                        .eq(CartPO::getSelected, true))
                .stream()
                .map(CartRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public Optional<CartItem> findByUserIdAndSkuId(Long userId, Long skuId) {
        CartPO po = cartMapper.selectOne(new LambdaQueryWrapper<CartPO>()
                .eq(CartPO::getUserId, userId)
                .eq(CartPO::getSkuId, skuId));
        return po == null ? Optional.empty() : Optional.of(toDomain(po));
    }

    @Override
    public void save(CartItem item) {
        cartMapper.insert(toPO(item));
    }

    @Override
    public void incrementQuantity(Long id, int quantity) {
        cartMapper.update(null, new LambdaUpdateWrapper<CartPO>()
                .eq(CartPO::getId, id)
                .setSql("quantity = quantity + " + quantity));
    }

    @Override
    public void updateQuantity(Long id, int quantity) {
        cartMapper.update(null, new LambdaUpdateWrapper<CartPO>()
                .eq(CartPO::getId, id)
                .set(CartPO::getQuantity, quantity));
    }

    @Override
    public void delete(Long id) {
        cartMapper.deleteById(id);
    }

    @Override
    public void deleteByUserIdAndSkuId(Long userId, Long skuId) {
        cartMapper.delete(new LambdaQueryWrapper<CartPO>()
                .eq(CartPO::getUserId, userId)
                .eq(CartPO::getSkuId, skuId));
    }

    @Override
    public void updateSelected(Long userId, Long skuId, boolean selected) {
        cartMapper.update(null, new LambdaUpdateWrapper<CartPO>()
                .eq(CartPO::getUserId, userId)
                .eq(CartPO::getSkuId, skuId)
                .set(CartPO::getSelected, selected));
    }

    @Override
    public void clearByUserId(Long userId) {
        cartMapper.delete(new LambdaQueryWrapper<CartPO>()
                .eq(CartPO::getUserId, userId));
    }

    @Override
    public void deleteByIds(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        cartMapper.delete(new LambdaQueryWrapper<CartPO>()
                .eq(CartPO::getUserId, userId)
                .in(CartPO::getId, ids));
    }

    private static CartItem toDomain(CartPO po) {
        return CartItem.rehydrate(po.getId(), po.getUserId(), po.getSkuId(), po.getQuantity(),
                po.getSelected(), po.getCreateTime(), po.getUpdateTime());
    }

    private static CartPO toPO(CartItem item) {
        CartPO po = new CartPO();
        po.setId(item.getId());
        po.setUserId(item.getUserId());
        po.setSkuId(item.getSkuId());
        po.setQuantity(item.getQuantity());
        po.setSelected(item.getSelected());
        return po;
    }
}
