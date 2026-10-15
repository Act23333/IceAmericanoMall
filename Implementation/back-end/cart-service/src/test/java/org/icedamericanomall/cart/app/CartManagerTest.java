package org.icedamericanomall.cart.app;

import org.icedamericanomall.cart.adapter.assembler.CartConverter;
import org.icedamericanomall.cart.adapter.assembler.CartConverterImpl;
import org.icedamericanomall.cart.adapter.request.CartUpdateReq;
import org.icedamericanomall.cart.adapter.response.CartItemVO;
import org.icedamericanomall.cart.adapter.response.CartVO;
import org.icedamericanomall.cart.domain.model.cart.CartItem;
import org.icedamericanomall.cart.domain.repository.CartRepository;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.dto.SkuCO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CartManager 购物车编排单元测试 —— Mock 仓储与 SkuClient，验证商品回填、金额计算与全选语义。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartManager 购物车编排单元测试")
class CartManagerTest {

    private static final Long USER_ID = 1L;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private SkuClient skuClient;

    private CartManager cartManager;

    /** 真实 MapStruct 转换器（聚合 → VO 映射不属于被测逻辑）。 */
    private final CartConverter converter = new CartConverterImpl();

    @BeforeEach
    void setUp() {
        cartManager = new CartManager(cartRepository, skuClient, converter);
    }

    private CartItem item(Long skuId, int quantity, boolean selected) {
        return CartItem.rehydrate(null, USER_ID, skuId, quantity, selected, null, null);
    }

    private SkuCO sku(Long id, Integer price) {
        SkuCO sku = new SkuCO();
        sku.setId(id);
        sku.setPrice(price);
        sku.setProductName("商品" + id);
        sku.setSpec("黑色 128G");
        sku.setImage("img" + id + ".jpg");
        return sku;
    }

    @Test
    @DisplayName("购物车视图 — 批量回填商品信息并计算小计/总价/选中总价")
    void shouldEnrichAndComputeAmounts_whenCartHasItems() {
        when(cartRepository.findByUserIdOrderByCreateTimeDesc(USER_ID)).thenReturn(List.of(
                item(100L, 2, true),
                item(200L, 1, true)));
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(100L, 1000), sku(200L, 500)));

        CartVO vo = cartManager.getCartView(USER_ID);

        CartItemVO first = vo.getItems().get(0);
        assertEquals("商品100", first.getProductName());
        assertEquals("黑色 128G", first.getSpec());
        assertEquals("img100.jpg", first.getImage());
        assertEquals(Integer.valueOf(1000), first.getPrice());
        assertEquals(Integer.valueOf(2000), first.getSubTotal());
        assertEquals(2500, vo.getTotalPrice().intValue());
        assertEquals(2500, vo.getSelectedPrice().intValue());
        assertTrue(vo.getAllSelected());
    }

    @Test
    @DisplayName("购物车视图 — SKU已下架（回填不到价格）时该项不计价且不得全选")
    void shouldMarkAllSelectedFalse_whenSomeItemHasNoPrice() {
        when(cartRepository.findByUserIdOrderByCreateTimeDesc(USER_ID)).thenReturn(List.of(
                item(100L, 2, true),
                item(200L, 1, true)));
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(100L, 1000)));

        CartVO vo = cartManager.getCartView(USER_ID);

        assertEquals(2000, vo.getTotalPrice().intValue());
        assertEquals(2000, vo.getSelectedPrice().intValue());
        assertFalse(vo.getAllSelected(), "存在无法计价（下架/删除）的商品时不得标记全选");
        assertNull(vo.getItems().get(1).getPrice());
        assertEquals(0, vo.getItems().get(1).getSubTotal().intValue());
    }

    @Test
    @DisplayName("购物车视图 — 存在未选中项时 allSelected=false，选中总价只累计选中项")
    void shouldComputeSelectedPriceOnly_whenPartiallySelected() {
        when(cartRepository.findByUserIdOrderByCreateTimeDesc(USER_ID)).thenReturn(List.of(
                item(100L, 1, true),
                item(200L, 3, false)));
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(100L, 1000), sku(200L, 800)));

        CartVO vo = cartManager.getCartView(USER_ID);

        assertEquals(3400, vo.getTotalPrice().intValue());
        assertEquals(1000, vo.getSelectedPrice().intValue());
        assertFalse(vo.getAllSelected());
    }

    @Test
    @DisplayName("购物车视图 — 空购物车返回0金额且不调用 item-service")
    void shouldReturnEmptyView_whenCartIsEmpty() {
        when(cartRepository.findByUserIdOrderByCreateTimeDesc(USER_ID)).thenReturn(List.of());

        CartVO vo = cartManager.getCartView(USER_ID);

        assertTrue(vo.getItems().isEmpty());
        assertEquals(0, vo.getTotalPrice().intValue());
        assertEquals(0, vo.getSelectedPrice().intValue());
        assertFalse(vo.getAllSelected());
        verify(skuClient, never()).getSkuListByIds(anyList());
    }

    @Test
    @DisplayName("加购 — 已存在同 SKU：原子累加数量而非新增行（去重合并）")
    void shouldIncrementQuantity_whenSameSkuExists() {
        CartItem existing = item(100L, 2, true);
        when(cartRepository.findByUserIdAndSkuId(USER_ID, 100L)).thenReturn(java.util.Optional.of(existing));

        var req = new org.icedamericanomall.cart.adapter.request.CartAddReq();
        req.setSkuId(100L);
        req.setQuantity(3);
        cartManager.addItem(USER_ID, req);

        verify(cartRepository).incrementQuantity(existing.getId(), 3);
        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("加购 — 并发撞 uk_user_sku：重读最新行后增量合并（不抛 500）")
    void shouldMergeConcurrentAdd_whenDuplicateKeyOnInsert() {
        CartItem concurrent = item(100L, 2, true);
        when(cartRepository.findByUserIdAndSkuId(USER_ID, 100L))
                .thenReturn(java.util.Optional.empty())
                .thenReturn(java.util.Optional.of(concurrent));
        doThrow(new org.springframework.dao.DuplicateKeyException("uk_user_sku"))
                .when(cartRepository).save(any());

        var req = new org.icedamericanomall.cart.adapter.request.CartAddReq();
        req.setSkuId(100L);
        req.setQuantity(3);
        cartManager.addItem(USER_ID, req);

        verify(cartRepository).save(any());
        verify(cartRepository).incrementQuantity(concurrent.getId(), 3);
    }

    @Test
    @DisplayName("修改数量 — 数量≤0 删除该项（Controller 不拦截）")
    void shouldDelete_whenQuantityNotPositive() {
        CartItem existing = item(100L, 2, true);
        when(cartRepository.findByUserIdAndSkuId(USER_ID, 100L)).thenReturn(java.util.Optional.of(existing));

        CartUpdateReq req = new CartUpdateReq();
        req.setSkuId(100L);
        req.setQuantity(0);
        cartManager.updateItem(USER_ID, req);

        verify(cartRepository).delete(existing.getId());
        verify(cartRepository, never()).updateQuantity(anyLong(), anyInt());
    }

    @Test
    @DisplayName("修改数量 — SKU 不存在时空操作")
    void shouldBeNoOp_whenSkuNotInCart() {
        when(cartRepository.findByUserIdAndSkuId(USER_ID, 100L)).thenReturn(java.util.Optional.empty());

        CartUpdateReq req = new CartUpdateReq();
        req.setSkuId(100L);
        req.setQuantity(5);
        cartManager.updateItem(USER_ID, req);

        verify(cartRepository, never()).updateQuantity(anyLong(), anyInt());
        verify(cartRepository, never()).delete(anyLong());
    }

    @Test
    @DisplayName("购物车视图 — product_name 是 I13 的纯透传：上游为空则线上为空（缺陷根因在 item-service，本侧无兜底）")
    void shouldPassThroughProductName_fromUpstreamSkuCo() {
        when(cartRepository.findByUserIdOrderByCreateTimeDesc(USER_ID))
                .thenReturn(List.of(item(100L, 1, true)));
        // 修复前的 I13 真实返回形状：sku 表无商品名 → product_name 恒 null
        SkuCO upstream = sku(100L, 1000);
        upstream.setProductName(null);
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(upstream));

        CartItemVO item = cartManager.getCartView(USER_ID).getItems().get(0);

        assertNull(item.getProductName(), "购物车不补商品名：上游为空即线上为空 —— 修复必须落在 item-service I13");
        assertEquals(Integer.valueOf(1000), item.getPrice(), "商品名缺失不影响计价");
    }

    @Test
    @DisplayName("购物车视图 — I13 修复后（product_name 非空）原样透出到 product_name")
    void shouldExposeProductName_whenUpstreamSkuCoFilled() {
        when(cartRepository.findByUserIdOrderByCreateTimeDesc(USER_ID))
                .thenReturn(List.of(item(100L, 1, true)));
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(100L, 1000)));

        CartItemVO item = cartManager.getCartView(USER_ID).getItems().get(0);

        assertEquals("商品100", item.getProductName());
    }
}
