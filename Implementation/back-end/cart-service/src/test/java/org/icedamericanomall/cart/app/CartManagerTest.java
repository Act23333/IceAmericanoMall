package org.icedamericanomall.cart.app;

import org.icedamericanomall.cart.adapter.assembler.CartConverter;
import org.icedamericanomall.cart.adapter.assembler.CartConverterImpl;
import org.icedamericanomall.cart.adapter.request.CartAddReq;
import org.icedamericanomall.cart.adapter.request.CartUpdateReq;
import org.icedamericanomall.cart.adapter.response.CartItemVO;
import org.icedamericanomall.cart.adapter.response.CartVO;
import org.icedamericanomall.cart.app.dto.SkuView;
import org.icedamericanomall.cart.app.port.CartSkuViewQuery;
import org.icedamericanomall.cart.domain.model.cart.CartItem;
import org.icedamericanomall.cart.domain.repository.CartRepository;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CartManager 购物车编排单元测试 —— Mock 仓储与 SKU 读模型，验证
 * ①商品回填与金额/全选语义；②业务键（ADR-061）：入参解析 fail-closed、出参一律业务键、
 * 展示回填 fail-open 且<b>不缩行</b>。
 *
 * <p>测试使用<b>真实 SkuView 值对象</b>与真实 MapStruct 转换器（映射不属于被测逻辑），
 * 且让技术键与业务键<b>取值刻意不同</b>（技术 100L vs 业务 32-hex）——
 * 于是「技术键被误当业务键输出」这类回归必然失败，无需靠人工填值制造假绿。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartManager 购物车编排单元测试")
class CartManagerTest {

    private static final Long USER_ID = 1L;
    private static final Long SKU_PK_A = 100L;
    private static final Long SKU_PK_B = 200L;
    private static final String SKU_ID_A = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";
    private static final String SKU_ID_B = "112233445566778899aabbccddeeff00";
    private static final String SPU_ID_A = "aabbccddeeff00112233445566778899";

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartSkuViewQuery cartSkuViewQuery;

    private CartManager cartManager;

    /** 真实 MapStruct 转换器（聚合 → VO 映射不属于被测逻辑）。 */
    private final CartConverter converter = new CartConverterImpl();

    @BeforeEach
    void setUp() {
        cartManager = new CartManager(cartRepository, cartSkuViewQuery, converter);
    }

    private CartItem item(Long skuPk, int quantity, boolean selected) {
        return CartItem.rehydrate(null, USER_ID, skuPk, quantity, selected, null, null);
    }

    /** 读模型行：技术键（cart 侧关联）与业务键（出线格式）刻意不同值。 */
    private SkuView view(Long skuPk, String skuBusinessId, Integer price) {
        return new SkuView(skuPk, skuBusinessId, SPU_ID_A, "商品" + skuPk, "黑色 128G",
                price, "img" + skuPk + ".jpg");
    }

    private static Map<Long, SkuView> byPk(SkuView... views) {
        Map<Long, SkuView> map = new LinkedHashMap<>();
        for (SkuView v : views) {
            map.put(v.skuTechnicalId(), v);
        }
        return map;
    }

    private static Map<String, SkuView> byBusiness(SkuView... views) {
        Map<String, SkuView> map = new LinkedHashMap<>();
        for (SkuView v : views) {
            map.put(v.skuBusinessId(), v);
        }
        return map;
    }

    @Test
    @DisplayName("购物车视图 — 批量回填商品信息并计算小计/总价/选中总价，且 sku_id 出业务键")
    void shouldEnrichAndComputeAmounts_whenCartHasItems() {
        when(cartRepository.findByUserIdOrderByCreateTimeDesc(USER_ID)).thenReturn(List.of(
                item(SKU_PK_A, 2, true),
                item(SKU_PK_B, 1, true)));
        when(cartSkuViewQuery.findBySkuIds(List.of(SKU_PK_A, SKU_PK_B)))
                .thenReturn(byPk(view(SKU_PK_A, SKU_ID_A, 1000), view(SKU_PK_B, SKU_ID_B, 500)));

        CartVO vo = cartManager.getCartView(USER_ID);

        CartItemVO first = vo.getItems().get(0);
        assertEquals(SKU_ID_A, first.getSkuId(), "ADR-061 §决策 2：出线格式是业务键，不是技术键");
        assertEquals(SPU_ID_A, first.getSpuId());
        assertEquals("商品100", first.getSpuName());
        assertEquals("黑色 128G", first.getSpec());
        assertEquals("img100.jpg", first.getImage());
        assertEquals(Integer.valueOf(1000), first.getPrice());
        assertEquals(Integer.valueOf(2000), first.getSubTotal());
        assertEquals(2500, vo.getTotalPrice().intValue());
        assertEquals(2500, vo.getSelectedPrice().intValue());
        assertTrue(vo.getAllSelected());
    }

    @Test
    @DisplayName("购物车视图 — SKU已下架（回填不到价格）时该项不计价且不得全选；sku_id 留空（不回落技术键）")
    void shouldMarkAllSelectedFalse_whenSomeItemHasNoPrice() {
        when(cartRepository.findByUserIdOrderByCreateTimeDesc(USER_ID)).thenReturn(List.of(
                item(SKU_PK_A, 2, true),
                item(SKU_PK_B, 1, true)));
        when(cartSkuViewQuery.findBySkuIds(List.of(SKU_PK_A, SKU_PK_B)))
                .thenReturn(byPk(view(SKU_PK_A, SKU_ID_A, 1000)));

        CartVO vo = cartManager.getCartView(USER_ID);

        assertEquals(2000, vo.getTotalPrice().intValue());
        assertEquals(2000, vo.getSelectedPrice().intValue());
        assertFalse(vo.getAllSelected(), "存在无法计价（下架/删除）的商品时不得标记全选");
        assertNull(vo.getItems().get(1).getPrice());
        assertEquals(0, vo.getItems().get(1).getSubTotal().intValue());
        assertNull(vo.getItems().get(1).getSkuId(), "查不到即留空，绝不回落技术键（ADR-061 §决策 3）");
    }

    @Test
    @DisplayName("购物车视图 — 存在未选中项时 allSelected=false，选中总价只累计选中项")
    void shouldComputeSelectedPriceOnly_whenPartiallySelected() {
        when(cartRepository.findByUserIdOrderByCreateTimeDesc(USER_ID)).thenReturn(List.of(
                item(SKU_PK_A, 1, true),
                item(SKU_PK_B, 3, false)));
        when(cartSkuViewQuery.findBySkuIds(List.of(SKU_PK_A, SKU_PK_B)))
                .thenReturn(byPk(view(SKU_PK_A, SKU_ID_A, 1000), view(SKU_PK_B, SKU_ID_B, 800)));

        CartVO vo = cartManager.getCartView(USER_ID);

        assertEquals(3400, vo.getTotalPrice().intValue());
        assertEquals(1000, vo.getSelectedPrice().intValue());
        assertFalse(vo.getAllSelected());
    }

    @Test
    @DisplayName("购物车视图 — 空购物车返回0金额且不查 SKU 读模型")
    void shouldReturnEmptyView_whenCartIsEmpty() {
        when(cartRepository.findByUserIdOrderByCreateTimeDesc(USER_ID)).thenReturn(List.of());

        CartVO vo = cartManager.getCartView(USER_ID);

        assertTrue(vo.getItems().isEmpty());
        assertEquals(0, vo.getTotalPrice().intValue());
        assertEquals(0, vo.getSelectedPrice().intValue());
        assertFalse(vo.getAllSelected());
        verify(cartSkuViewQuery, never()).findBySkuIds(any());
    }

    @Test
    @DisplayName("加购 — 已存在同 SKU：原子累加数量而非新增行（去重合并）")
    void shouldIncrementQuantity_whenSameSkuExists() {
        CartItem existing = item(SKU_PK_A, 2, true);
        when(cartSkuViewQuery.findBySkuBusinessIds(List.of(SKU_ID_A)))
                .thenReturn(byBusiness(view(SKU_PK_A, SKU_ID_A, 1000)));
        when(cartRepository.findByUserIdAndSkuId(USER_ID, SKU_PK_A)).thenReturn(java.util.Optional.of(existing));

        CartAddReq req = new CartAddReq();
        req.setSkuId(SKU_ID_A);
        req.setQuantity(3);
        cartManager.addItem(USER_ID, req);

        verify(cartRepository).incrementQuantity(existing.getId(), 3);
        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("加购 — 业务键解析不到（SKU 不存在/格式非法）→ 抛 SKU_NOT_FOUND，不落脏行（fail-closed）")
    void shouldThrow_whenSkuBusinessIdUnresolvable() {
        when(cartSkuViewQuery.findBySkuBusinessIds(List.of("prod-001"))).thenReturn(Map.of());

        CartAddReq req = new CartAddReq();
        req.setSkuId("prod-001");
        req.setQuantity(1);

        BizException ex = assertThrows(BizException.class, () -> cartManager.addItem(USER_ID, req));
        assertEquals(ErrorCode.SKU_NOT_FOUND, ex.getErrorCode());
        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("加购 — 并发撞 uk_user_sku：重读最新行后增量合并（不抛 500）")
    void shouldMergeConcurrentAdd_whenDuplicateKeyOnInsert() {
        CartItem concurrent = item(SKU_PK_A, 2, true);
        when(cartSkuViewQuery.findBySkuBusinessIds(List.of(SKU_ID_A)))
                .thenReturn(byBusiness(view(SKU_PK_A, SKU_ID_A, 1000)));
        when(cartRepository.findByUserIdAndSkuId(USER_ID, SKU_PK_A))
                .thenReturn(java.util.Optional.empty())
                .thenReturn(java.util.Optional.of(concurrent));
        doThrow(new org.springframework.dao.DuplicateKeyException("uk_user_sku"))
                .when(cartRepository).save(any());

        CartAddReq req = new CartAddReq();
        req.setSkuId(SKU_ID_A);
        req.setQuantity(3);
        cartManager.addItem(USER_ID, req);

        verify(cartRepository).save(any());
        verify(cartRepository).incrementQuantity(concurrent.getId(), 3);
    }

    @Test
    @DisplayName("修改数量 — 数量≤0 删除该项（Controller 不拦截）")
    void shouldDelete_whenQuantityNotPositive() {
        CartItem existing = item(SKU_PK_A, 2, true);
        when(cartSkuViewQuery.findBySkuBusinessIds(List.of(SKU_ID_A)))
                .thenReturn(byBusiness(view(SKU_PK_A, SKU_ID_A, 1000)));
        when(cartRepository.findByUserIdAndSkuId(USER_ID, SKU_PK_A)).thenReturn(java.util.Optional.of(existing));

        CartUpdateReq req = new CartUpdateReq();
        req.setSkuId(SKU_ID_A);
        req.setQuantity(0);
        cartManager.updateItem(USER_ID, req);

        verify(cartRepository).delete(existing.getId());
        verify(cartRepository, never()).updateQuantity(anyLong(), anyInt());
    }

    @Test
    @DisplayName("修改数量 — SKU 不在购物车时空操作")
    void shouldBeNoOp_whenSkuNotInCart() {
        when(cartSkuViewQuery.findBySkuBusinessIds(List.of(SKU_ID_A)))
                .thenReturn(byBusiness(view(SKU_PK_A, SKU_ID_A, 1000)));
        when(cartRepository.findByUserIdAndSkuId(USER_ID, SKU_PK_A)).thenReturn(java.util.Optional.empty());

        CartUpdateReq req = new CartUpdateReq();
        req.setSkuId(SKU_ID_A);
        req.setQuantity(5);
        cartManager.updateItem(USER_ID, req);

        verify(cartRepository, never()).updateQuantity(anyLong(), anyInt());
        verify(cartRepository, never()).delete(anyLong());
    }

    @Test
    @DisplayName("修改数量 — SKU 业务键解析不到 → 幂等空操作（目标行本就不存在）")
    void shouldBeNoOp_whenBusinessIdUnresolvable() {
        when(cartSkuViewQuery.findBySkuBusinessIds(List.of(SKU_ID_A))).thenReturn(Map.of());

        CartUpdateReq req = new CartUpdateReq();
        req.setSkuId(SKU_ID_A);
        req.setQuantity(5);
        cartManager.updateItem(USER_ID, req);

        verify(cartRepository, never()).updateQuantity(anyLong(), anyInt());
    }

    @Test
    @DisplayName("购物车视图 — 读模型抛异常 → 展示字段置空、列表照常返回（fail-open，不缩行，ADR-057）")
    void shouldDegradeGracefully_whenSkuEnrichmentFails() {
        when(cartRepository.findByUserIdOrderByCreateTimeDesc(USER_ID)).thenReturn(List.of(
                item(SKU_PK_A, 2, true),
                item(SKU_PK_B, 1, true)));
        when(cartSkuViewQuery.findBySkuIds(any(Collection.class)))
                .thenThrow(new BizException(ErrorCode.SYSTEM_BUSY, "商品服务暂不可用，请稍后重试"));

        CartVO vo = assertDoesNotThrow(() -> cartManager.getCartView(USER_ID),
                "购物车列表不得因展示字段回填失败而整体不可用");

        assertEquals(2, vo.getItems().size(), "降级不丢项：购物车项来自本地表");
        assertNull(vo.getItems().get(0).getSkuId());
        assertNull(vo.getItems().get(0).getSpuName());
        assertNull(vo.getItems().get(0).getPrice());
        assertEquals(0, vo.getItems().get(0).getSubTotal().intValue());
        assertEquals(0, vo.getTotalPrice().intValue(), "取不到单价即不计价（宁可少计不可错计）");
        assertEquals(0, vo.getSelectedPrice().intValue());
        assertFalse(vo.getAllSelected(), "无法计价时不得标记全选");
    }

    @Test
    @DisplayName("购物车视图 — SPU 名是读模型纯透传：上游为空则线上为空（本侧无兜底）")
    void shouldPassThroughSpuName_fromReadModel() {
        when(cartRepository.findByUserIdOrderByCreateTimeDesc(USER_ID))
                .thenReturn(List.of(item(SKU_PK_A, 1, true)));
        SkuView upstream = new SkuView(SKU_PK_A, SKU_ID_A, SPU_ID_A, null, "黑色 128G",
                1000, "img100.jpg");
        when(cartSkuViewQuery.findBySkuIds(List.of(SKU_PK_A))).thenReturn(byPk(upstream));

        CartItemVO item = cartManager.getCartView(USER_ID).getItems().get(0);

        assertNull(item.getSpuName(), "购物车不补商品名：上游为空即线上为空 —— 修复必须落在 item-service I13");
        assertEquals(Integer.valueOf(1000), item.getPrice(), "商品名缺失不影响计价");
        assertEquals(SKU_ID_A, item.getSkuId(), "业务键照常输出");
    }

    @Test
    @DisplayName("下单取数（I14）— CartItemCO.skuId 出业务键，供 trade 按 sku_ids 口径取数")
    void shouldExposeBusinessKeyInSelectedItemCos() {
        when(cartRepository.findSelectedByUserId(USER_ID)).thenReturn(List.of(item(SKU_PK_A, 2, true)));
        when(cartSkuViewQuery.findBySkuIds(List.of(SKU_PK_A)))
                .thenReturn(byPk(view(SKU_PK_A, SKU_ID_A, 1000)));

        var cos = cartManager.getSelectedItemCos(USER_ID);

        assertEquals(1, cos.size());
        assertEquals(SKU_ID_A, cos.get(0).getSkuId(), "I14 出参必须是业务键（trade 侧据此查 I13）");
    }
}
