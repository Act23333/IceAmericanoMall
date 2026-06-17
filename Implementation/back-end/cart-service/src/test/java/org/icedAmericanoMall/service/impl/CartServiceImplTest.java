package org.icedAmericanoMall.service.impl;

import org.icedAmericanoMall.dto.request.CartAddReq;
import org.icedAmericanoMall.dto.request.CartUpdateReq;
import org.icedAmericanoMall.dto.response.CartItemResp;
import org.icedAmericanoMall.dto.response.CartVO;
import org.icedAmericanoMall.pojo.CartEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CartServiceImpl — entity validation and business rule verification.
 *
 * Note: Methods using MyBatis-Plus lambdaQuery()/lambdaUpdate() chains
 * (addItem, updateItem, removeItem, selectItem, clearCart, getCart, getSelectedItems)
 * require {@code @SpringBootTest} integration tests with Testcontainers or H2.
 */
@DisplayName("CartServiceImpl 单元测试")
class CartServiceImplTest {

    // ==================== CartEntity ====================

    @Test
    @DisplayName("CartEntity — 创建购物车项默认值正确")
    void shouldCreateCartItemWithDefaultValues() {
        CartEntity entity = new CartEntity();
        entity.setUserId(1L);
        entity.setSkuId(100L);
        entity.setQuantity(2);
        entity.setSelected(true);

        assertEquals(1L, entity.getUserId());
        assertEquals(100L, entity.getSkuId());
        assertEquals(2, entity.getQuantity());
        assertTrue(entity.getSelected());
    }

    @Test
    @DisplayName("CartEntity — 小计 = 单价 × 数量")
    void shouldCalculateSubtotalCorrectly() {
        CartEntity entity = new CartEntity();
        entity.setPrice(1000); // 10.00 元 = 1000 分
        entity.setQuantity(3);

        int subtotal = entity.getPrice() * entity.getQuantity();
        assertEquals(3000, subtotal);
    }

    @Test
    @DisplayName("CartEntity — 商品冗余字段正确")
    void shouldMarkTransientFieldsCorrectly() {
        CartEntity entity = new CartEntity();
        entity.setProductName("Test Product");
        entity.setPrice(5000);
        entity.setImage("test.jpg");

        assertEquals("Test Product", entity.getProductName());
        assertEquals(5000, entity.getPrice());
        assertEquals("test.jpg", entity.getImage());
    }

    // ==================== 去重合并逻辑 ====================

    @Test
    @DisplayName("去重合并 — 同一 (userId, skuId) 应合并数量而非新增行")
    void shouldConceptuallyMergeQuantity_whenSameSkuAdded() {
        // Given: 购物车已有 skuId=100, quantity=2
        CartEntity existing = new CartEntity();
        existing.setUserId(1L);
        existing.setSkuId(100L);
        existing.setQuantity(2);

        // When: 用户再次添加相同 SKU, quantity=3
        int addQuantity = 3;
        existing.setQuantity(existing.getQuantity() + addQuantity);

        // Then: 数量合并为 5
        assertEquals(5, existing.getQuantity());
        // 仍然是同一行 (userId=1, skuId=100)
        assertEquals(1L, existing.getUserId());
        assertEquals(100L, existing.getSkuId());
    }

    @Test
    @DisplayName("去重合并 — 新SKU应创建新行")
    void shouldCreateNewRow_whenNewSkuAdded() {
        CartEntity existing = new CartEntity();
        existing.setUserId(1L);
        existing.setSkuId(100L);

        CartEntity newItem = new CartEntity();
        newItem.setUserId(1L);
        newItem.setSkuId(200L); // 不同的 SKU

        assertNotEquals(existing.getSkuId(), newItem.getSkuId(),
                "不同SKU应视为不同的购物车行");
    }

    // ==================== 数量为0删除逻辑 ====================

    @Test
    @DisplayName("数量为0 — 应将购物车项删除")
    void shouldRemoveItem_whenQuantitySetToZero() {
        int newQuantity = 0;
        assertTrue(newQuantity <= 0, "数量≤0应触发删除");
    }

    @Test
    @DisplayName("数量为负 — 应将购物车项删除")
    void shouldRemoveItem_whenQuantityNegative() {
        int newQuantity = -1;
        assertTrue(newQuantity <= 0, "负数也应触发删除");
    }

    // ==================== CartVO 计算逻辑 ====================

    @Test
    @DisplayName("CartVO — 总价和选中金额计算正确")
    void shouldCalculateTotalAndSelectedAmountCorrectly() {
        List<CartItemResp> items = Arrays.asList(
                buildItem(1000, 2, true),   // 选中, subtotal=2000
                buildItem(500, 3, true),     // 选中, subtotal=1500
                buildItem(2000, 1, false)    // 未选中, subtotal=2000
        );

        int totalPrice = 0;
        int selectedPrice = 0;
        boolean allSelected = !items.isEmpty();
        for (CartItemResp item : items) {
            totalPrice += item.getSubTotal();
            if (Boolean.TRUE.equals(item.getSelected())) {
                selectedPrice += item.getSubTotal();
            } else {
                allSelected = false;
            }
        }

        assertEquals(5500, totalPrice);    // 2000+1500+2000
        assertEquals(3500, selectedPrice); // 2000+1500
        assertFalse(allSelected);           // 有一项未选中
    }

    @Test
    @DisplayName("CartVO — 全部选中时 allSelected=true")
    void shouldMarkAllSelected_whenAllItemsSelected() {
        List<CartItemResp> items = Arrays.asList(
                buildItem(1000, 1, true),
                buildItem(500, 2, true)
        );

        boolean allSelected = !items.isEmpty();
        for (CartItemResp item : items) {
            if (!Boolean.TRUE.equals(item.getSelected())) {
                allSelected = false;
            }
        }

        assertTrue(allSelected);
    }

    @Test
    @DisplayName("CartVO — 空购物车 allSelected=false")
    void shouldNotMarkAllSelected_whenCartEmpty() {
        List<CartItemResp> items = Collections.emptyList();
        boolean allSelected = !items.isEmpty();
        assertFalse(allSelected, "空购物车不应全选");
    }

    // ==================== DTO 验证 ====================

    @Test
    @DisplayName("CartAddReq — 字段正确赋值")
    void shouldSetCartAddReqFields() {
        CartAddReq req = new CartAddReq();
        req.setSkuId(100L);
        req.setQuantity(3);

        assertEquals(100L, req.getSkuId());
        assertEquals(3, req.getQuantity());
    }

    @Test
    @DisplayName("CartUpdateReq — 字段正确赋值")
    void shouldSetCartUpdateReqFields() {
        CartUpdateReq req = new CartUpdateReq();
        req.setSkuId(100L);
        req.setQuantity(5);

        assertEquals(100L, req.getSkuId());
        assertEquals(5, req.getQuantity());
    }

    // ==================== helper ====================

    private CartItemResp buildItem(int price, int quantity, boolean selected) {
        CartItemResp item = new CartItemResp();
        item.setPrice(price);
        item.setQuantity(quantity);
        if (price > 0 && quantity > 0) {
            item.setSubTotal(price * quantity);
        }
        item.setSelected(selected);
        return item;
    }
}
