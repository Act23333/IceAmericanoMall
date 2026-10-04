package org.icedamericanomall.cart.domain.repository;

import org.icedamericanomall.cart.domain.model.cart.CartItem;

import java.util.List;
import java.util.Optional;

/**
 * 购物车仓储（领域端口）—— 聚合的持久化契约。
 *
 * <p>实现方：{@code infrastructure.persistence.CartRepositoryImpl}（MyBatis-Plus）。
 * 并发语义：{@link #save} 撞 {@code uk_user_sku} 时抛 {@code DuplicateKeyException}，
 * 由调用方转为增量合并；{@link #incrementQuantity} 为数据库原子累加。
 */
public interface CartRepository {

    /** 用户全部购物车项（按加入时间倒序）。 */
    List<CartItem> findByUserIdOrderByCreateTimeDesc(Long userId);

    /** 用户已选中的购物车项（下单取数）。 */
    List<CartItem> findSelectedByUserId(Long userId);

    /** 按业务唯一键 (userId, skuId) 定位。 */
    Optional<CartItem> findByUserIdAndSkuId(Long userId, Long skuId);

    /** 新增购物车项（默认选中由聚合工厂保证）。 */
    void save(CartItem item);

    /** 原子累加数量（去重合并，DB 侧 SET_INCR）。 */
    void incrementQuantity(Long id, int quantity);

    /** 直接设置数量（≤0 的删除分支由应用层先行裁定）。 */
    void updateQuantity(Long id, int quantity);

    /** 删除单条。 */
    void delete(Long id);

    /** 删除用户指定 SKU 的购物车项。 */
    void deleteByUserIdAndSkuId(Long userId, Long skuId);

    /** 切换选中状态。 */
    void updateSelected(Long userId, Long skuId, boolean selected);

    /** 清空用户购物车。 */
    void clearByUserId(Long userId);

    /** 删除用户指定购物车项（仅删除已下单商品，京东口径）。 */
    void deleteByIds(Long userId, List<Long> ids);
}
