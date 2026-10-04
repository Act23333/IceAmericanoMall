package org.icedamericanomall.cart.domain.model.cart;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 购物车项聚合单元测试 —— 加购工厂缺省语义与重建回填。
 *
 * <p>历史 {@code CartServiceImplTest} 中的实体字段/小计算术断言已由
 * {@code CartManagerTest}（回填与小计）与 {@code CartManagerH2Test}（SQL 行为）等价承接。
 */
@DisplayName("购物车项聚合测试")
class CartItemTest {

    @Test
    @DisplayName("create — 加购工厂默认选中，字段正确")
    void shouldCreateWithDefaults() {
        CartItem item = CartItem.create(1L, 100L, 2);

        assertEquals(1L, item.getUserId());
        assertEquals(100L, item.getSkuId());
        assertEquals(2, item.getQuantity());
        assertTrue(item.getSelected(), "加购默认选中");
        assertNull(item.getId());
    }

    @Test
    @DisplayName("rehydrate — 仓储重建字段完整回填")
    void shouldRehydrateAllFields() {
        CartItem item = CartItem.rehydrate(9L, 1L, 100L, 3, false, null, null);

        assertEquals(9L, item.getId());
        assertEquals(100L, item.getSkuId());
        assertEquals(3, item.getQuantity());
        assertFalse(item.getSelected());
    }
}
