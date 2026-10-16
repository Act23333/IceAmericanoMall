package org.icedamericanomall.cart.adapter.assembler;

import org.icedamericanomall.cart.adapter.response.CartItemVO;
import org.icedamericanomall.cart.domain.model.cart.CartItem;
import org.icedamericanomall.dto.CartItemCO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CartConverter <b>真实现</b>契约测试 —— 用 MapStruct 生成的 {@link CartConverterImpl}（非 mock）
 * 锁死「聚合 → CO」的字段映射契约。
 *
 * <p><b>为什么必须禁止 mock 转换器</b>：缺陷「{@code cartItemId} 恒 null → 下单后已购购物车项永不清除」
 * 的根因是源字段 {@code CartItem.id} 与目标字段 {@code CartItemCO.cartItemId} <b>名字不同、无法自动映射</b>。
 * 上游测试若 mock 掉转换器（或手工 {@code setCartItemId(...)} 后再断言），等于把待验证字段自己填好
 * 再断言——永远绿。本类只允许真实现 + 真聚合，任何映射缺口都会立刻变红。
 *
 * <p>配套防线（三层）：
 * <ol>
 *   <li>本类：真实现的字段级契约；</li>
 *   <li>{@code CartManagerH2Test}：真仓储 + 真转换器的端到端主键回传；</li>
 *   <li>parent pom {@code mapstruct.unmappedTargetPolicy=ERROR}：把映射缺口变成<b>构建失败</b>
 *       （去掉 {@code @Mapping(target = "cartItemId", source = "id")} 即编译不过）。</li>
 * </ol>
 */
@DisplayName("CartConverter 真实现契约测试（MapStruct Impl，非 mock）")
class CartConverterContractTest {

    /** 真实生成实现；绝不替换为 mock。 */
    private final CartConverter converter = new CartConverterImpl();

    private CartItem cartItem(Long id, Long skuId, int quantity, boolean selected) {
        return CartItem.rehydrate(id, 100L, skuId, quantity, selected,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 2, 0, 0));
    }

    @Test
    @DisplayName("itemToCO — CartItem(id=1) → cartItemId=1（id → cartItemId 必须显式映射）")
    void shouldMapCartItemId_whenConvertingToInternalCo() {
        CartItemCO co = converter.itemToCO(cartItem(1L, 1000L, 3, true));

        assertEquals(1L, co.getCartItemId(),
                "cartItemId 恒 null（源 id 未显式映射）会让 trade 侧删不到已购购物车项");
        // ADR-061 §决策 2 + ADR-058 §4：skuId 同名异义（源=技术键，目标=业务键）必须 ignore，
        // 由 CartManager 经读模型回填 —— 故此处恒为 null（绝不把技术键静默转成字符串）
        assertNull(co.getSkuId(), "技术键不得自动落进业务键字段（Long 1000 → \"1000\" 是最难排查的错误形态）");
        assertEquals(3, co.getQuantity());
    }

    @Test
    @DisplayName("itemsToCOs — 列表逐项走同一映射：每项 cartItemId 等于各自主键（不共享/不丢失）")
    void shouldMapEveryCartItemId_whenConvertingList() {
        List<CartItemCO> cos = converter.itemsToCOs(List.of(
                cartItem(1L, 1000L, 3, true),
                cartItem(2L, 1001L, 1, false)));

        assertEquals(2, cos.size());
        assertEquals(1L, cos.get(0).getCartItemId());
        assertEquals(2L, cos.get(1).getCartItemId());
        assertNull(cos.get(1).getSkuId(), "业务键由编排层回填，转换器不得写入技术键");
        assertTrue(cos.stream().allMatch(co -> co.getCartItemId() != null),
                "I14 明细不允许出现无主键项");
    }

    @Test
    @DisplayName("itemToCO — 商品快照字段按设计恒空（由 trade 侧从 I13 的 SkuCO 补齐，购物车不存快照）")
    void shouldLeaveSnapshotFieldsNull_whenConvertingToInternalCo() {
        CartItemCO co = converter.itemToCO(cartItem(1L, 1000L, 3, true));

        assertNull(co.getProductName(), "快照字段由下单侧以商品域为准，避免购物车侧快照腐化");
        assertNull(co.getSpec());
        assertNull(co.getImage());
        assertNull(co.getPrice());
        assertNull(co.getSpuId());
    }

    @Test
    @DisplayName("itemToVO — 展示字段按设计恒空（由 CartManager.enrich 读时回填，fail-open 口径 ADR-057）")
    void shouldLeaveDisplayFieldsNull_whenConvertingToVo() {
        CartItemVO vo = converter.itemToVO(cartItem(1L, 1000L, 3, true));

        assertNull(vo.getSkuId(), "SKU 业务键由编排层经读模型回填（技术键不得自动转换）");
        assertEquals(3, vo.getQuantity());
        assertTrue(vo.getSelected());
        assertNull(vo.getSpuName(), "SPU 名/业务键/规格/图片/单价/小计均由编排层批量回填");
        assertNull(vo.getSpec());
        assertNull(vo.getImage());
        assertNull(vo.getPrice());
        assertNull(vo.getSubTotal());
    }
}
