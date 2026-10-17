package org.icedamericanomall.trade.app.order.strategy;

import org.icedamericanomall.client.AddressClient;
import org.icedamericanomall.client.CartClient;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.CartItemCO;
import org.icedamericanomall.dto.SkuCO;
import org.icedamericanomall.trade.app.order.pricing.PricingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「SKU 不存在」在三条下单策略里的<b>错误承载</b>（2026-10-18 批次 ①「not-found 归位」）。
 *
 * <p><b>锁死的偏离</b>：三条策略此前都把「真·资源不存在」写成
 * {@code BUSINESS_EXECUTION_EXCEPTION}(3000 / HTTP 200) —— 与「业务规则拒绝」共用一个信封，
 * 下单失败时调用方（前端/网关）无法区分「商品被删了」与「规则不允许」。
 * 现统一归位为 {@code NO_RESOURCE_FOUND}(1103 / <b>HTTP 404</b>)。
 *
 * <p><b>为什么合一个测试类</b>：三条策略的这两行代码是同一处约定的三个副本
 * （{@code DirectOrderStrategy:41} / {@code FlashSaleOrderStrategy:38} /
 * {@code NormalCartOrderStrategy:69}），分开写三份只会让「其中一条忘了改」更不容易被看见；
 * 此处逐个点名断言，哪个策略退化就在失败信息里直接指出来。
 *
 * <p>拼团策略（{@code GroupBuyOrderStrategy#loadSku}，第 4 个副本）的同口径断言在
 * {@code GroupBuyOrderStrategyTest#shouldReject_whenSkuMissing} 内（本批改严）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("下单策略 — SKU 不存在一律 1103 / 404（不再是 3000 / 200）")
class OrderStrategySkuNotFoundTest {

    /** SKU 业务键（32-hex，ADR-061 §决策 2）。 */
    private static final String SKU_BIZ = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";
    /** 第二个 SKU 业务键（购物车路径用）。 */
    private static final String SKU_BIZ_2 = "112233445566778899aabbccddeeff00";

    @Mock
    private SkuClient skuClient;
    @Mock
    private AddressClient addressClient;
    @Mock
    private CartClient cartClient;
    @Mock
    private FreightPostageResolver freightPostageResolver;
    /** validate 阶段不应触发计价（buildOrder 才用），mock 足以表明该前提。 */
    @Mock
    private PricingService pricingService;

    private static OrderCreateContext directCtx() {
        OrderCreateContext ctx = new OrderCreateContext();
        ctx.setUserId(1L);
        ctx.setSkuId(SKU_BIZ);
        ctx.setQuantity(1);
        ctx.setAddressId(9L);
        return ctx;
    }

    /** 三条策略共用：断言 1103 + 404 + CLIENT，且绝不再是 3000。 */
    private static void assertNotFound(BizException ex, String where) {
        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode(),
                where + "：SKU 不存在必须抛 1103（NO_RESOURCE_FOUND），不得借用 3000");
        assertEquals(404, ex.getErrorCode().getHttpStatus().value(),
                where + "：not-found 语义必须真 404（09-constraints §三·六）");
        assertEquals(ErrorCategory.CLIENT, ErrorCategory.of(ex.getErrorCode()),
                where + "：404 → CLIENT，Feign 调用方据此透传，不改写为 SYSTEM_BUSY");
        assertNotEquals(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, ex.getErrorCode(),
                where + "：不得再与「业务规则拒绝」（3000 / 200）合流");
    }

    @Test
    @DisplayName("DirectOrderStrategy — 立即购买：SKU 不存在 → 1103 / 404，且不读地址")
    void shouldThrow404_whenDirectOrderSkuMissing() {
        DirectOrderStrategy strategy = new DirectOrderStrategy(
                skuClient, addressClient, pricingService, freightPostageResolver);
        when(skuClient.getSkuListByIds(List.of(SKU_BIZ))).thenReturn(List.of());

        BizException ex = assertThrows(BizException.class, () -> strategy.validate(directCtx()));

        assertNotFound(ex, "DirectOrderStrategy");
        verify(addressClient, never()).getAddress(anyLong());
    }

    @Test
    @DisplayName("FlashSaleOrderStrategy — 秒杀：SKU 不存在 → 1103 / 404，且不读地址")
    void shouldThrow404_whenFlashSaleSkuMissing() {
        FlashSaleOrderStrategy strategy = new FlashSaleOrderStrategy(skuClient, addressClient);
        OrderCreateContext ctx = directCtx();
        ctx.setFlashPrice(100);
        when(skuClient.getSkuListByIds(List.of(SKU_BIZ))).thenReturn(List.of());

        BizException ex = assertThrows(BizException.class, () -> strategy.validate(ctx));

        assertNotFound(ex, "FlashSaleOrderStrategy");
        verify(addressClient, never()).getAddress(anyLong());
    }

    @Test
    @DisplayName("NormalCartOrderStrategy — 购物车：命中数够但某行解析不到 SKU → 1103 / 404")
    void shouldThrow404_whenCartSkuMissing() {
        NormalCartOrderStrategy strategy = new NormalCartOrderStrategy(
                cartClient, skuClient, addressClient, pricingService, freightPostageResolver);
        OrderCreateContext ctx = new OrderCreateContext();
        ctx.setUserId(1L);
        ctx.setAddressId(9L);
        when(cartClient.getSelectedItems(1L)).thenReturn(List.of(cartItem(SKU_BIZ), cartItem(SKU_BIZ_2)));
        // 长度校验能过（2 == 2），但第二行在 map 里缺失 → 走「SKU 不存在」分支
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(SKU_BIZ), sku(SKU_BIZ)));

        BizException ex = assertThrows(BizException.class, () -> strategy.validate(ctx));

        assertNotFound(ex, "NormalCartOrderStrategy");
    }

    private static CartItemCO cartItem(String skuBiz) {
        CartItemCO item = new CartItemCO();
        item.setSkuId(skuBiz);
        item.setQuantity(1);
        return item;
    }

    private static SkuCO sku(String skuBiz) {
        SkuCO sku = new SkuCO();
        sku.setSkuId(skuBiz);
        sku.setSpuId("aabbccddeeff00112233445566778899");
        sku.setSellerId(9L);
        sku.setSpuName("商品");
        sku.setPrice(1000);
        sku.setStock(10);
        return sku;
    }
}
