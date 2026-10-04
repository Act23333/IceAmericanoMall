package org.icedamericanomall.cart.domain.model.cart;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 购物车项聚合根 —— 业务唯一键 (userId, skuId)，同 SKU 只有一行、数量累加。
 *
 * <p>业务规则内聚：加购默认选中；数量边界（≤0 触发删除）由应用层流程裁定，
 * 原子累加（SET_INCR）由仓储实现保证并发安全。持久化形态见
 * {@code infrastructure.persistence.CartPO}。
 */
@Getter
public class CartItem {

    private Long id;
    private Long userId;
    private Long skuId;
    private Integer quantity;
    private Boolean selected;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private CartItem() {
    }

    /**
     * 加购工厂：默认选中（加购即选中，京东口径）。
     */
    public static CartItem create(Long userId, Long skuId, Integer quantity) {
        CartItem item = new CartItem();
        item.userId = userId;
        item.skuId = skuId;
        item.quantity = quantity;
        item.selected = true;
        return item;
    }

    /** 仓储重建（读模型恢复）。 */
    public static CartItem rehydrate(Long id, Long userId, Long skuId, Integer quantity,
                                     Boolean selected, LocalDateTime createTime, LocalDateTime updateTime) {
        CartItem item = new CartItem();
        item.id = id;
        item.userId = userId;
        item.skuId = skuId;
        item.quantity = quantity;
        item.selected = selected;
        item.createTime = createTime;
        item.updateTime = updateTime;
        return item;
    }
}
