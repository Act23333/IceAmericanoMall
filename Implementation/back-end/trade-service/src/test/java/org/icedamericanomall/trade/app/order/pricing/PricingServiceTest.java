package org.icedamericanomall.trade.app.order.pricing;

import org.icedamericanomall.client.CouponClient;
import org.icedamericanomall.client.MemberClient;
import org.icedamericanomall.client.PromotionClient;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.MemberLevelCO;
import org.icedamericanomall.dto.PromotionCO;
import org.icedamericanomall.dto.PromotionQueryCO;
import org.icedamericanomall.trade.app.order.strategy.OrderCreateContext;
import org.icedamericanomall.trade.domain.enums.OrderTypeEnum;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link PricingService} 价格计算中心单元测试 —— 三优惠源解析与编排（ADR-043/044）：
 * I44 fail-closed 透传、I45 fail-open 默认不折扣、券核销 Feign 语义收编后原样保留。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PricingService — 价格计算中心（促销+会员+券）")
class PricingServiceTest {

    @Mock private PromotionClient promotionClient;
    @Mock private MemberClient memberClient;
    @Mock private CouponClient couponClient;

    private PricingService pricingService;

    @BeforeEach
    void setUp() {
        pricingService = new PricingService(promotionClient, memberClient, couponClient);
    }

    private OrderCreateContext ctx(int total) {
        OrderCreateContext c = new OrderCreateContext();
        c.setUserId(100L);
        c.setOrderType(OrderTypeEnum.NORMAL);
        c.setSellerId(9L);
        c.setTotalAmount(total);
        OrderCreateContext.CartItemSnapshot snap = new OrderCreateContext.CartItemSnapshot();
        snap.setProductId(11L);
        snap.setCategoryId(22L);
        c.setSnapshots(List.of(snap));
        return c;
    }

    private Order order() {
        Order o = new Order();
        o.setOrderNo("ORD-1");
        return o;
    }

    private PromotionCO promotion(int type, int threshold, int discountValue, int maxDiscount) {
        return new PromotionCO(1L, "店铺促销", 9L, type, threshold, discountValue, maxDiscount);
    }

    // ==================== 促销（I44） ====================

    @Test
    @DisplayName("带促销下单 — I44 返回满减活动（满10000减2000）→ discountAmount 命中 2000")
    void shouldReturnPromotionDiscount_whenActivePromotionExists() {
        when(promotionClient.queryActive(any(PromotionQueryCO.class)))
                .thenReturn(List.of(promotion(1, 10000, 2000, 0)));
        // I45 降级默认 NORMAL（fallback 行为模拟）：不折扣
        when(memberClient.getLevel(100L)).thenReturn(new MemberLevelCO("NORMAL", 10000, 100));

        int discount = pricingService.calculate(ctx(10000), order());

        assertEquals(2000, discount);
        verify(couponClient, never()).useCoupon(anyLong(), anyLong(), anyString(),
                anyInt(), anyInt(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("无促销 — I44 返回空集合（合法业务态）→ 促销减免 0，不报错")
    void shouldReturnZeroPromotionDiscount_whenNoActivePromotions() {
        when(promotionClient.queryActive(any(PromotionQueryCO.class))).thenReturn(List.of());
        when(memberClient.getLevel(100L)).thenReturn(new MemberLevelCO("NORMAL", 10000, 100));

        int discount = pricingService.calculate(ctx(10000), order());

        assertEquals(0, discount);
    }

    @Test
    @DisplayName("I44 fail-closed — 促销查询抛异常 → 透传使下单失败，且券尚未核销（时序保证）")
    void shouldPropagateException_whenPromotionQueryFails() {
        when(promotionClient.queryActive(any(PromotionQueryCO.class)))
                .thenThrow(new BizException(org.icedamericanomall.common.enums.ErrorCode.SYSTEM_BUSY,
                        "促销计价服务暂不可用"));

        assertThrows(BizException.class, () -> pricingService.calculate(ctx(10000), order()));

        verify(couponClient, never()).useCoupon(anyLong(), anyLong(), anyString(),
                anyInt(), anyInt(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("I44 查询入参 — 按单店铺 sellerId 构造 PromotionQueryCO")
    void shouldQueryPromotionBySellerId() {
        when(promotionClient.queryActive(any(PromotionQueryCO.class))).thenReturn(List.of());
        when(memberClient.getLevel(100L)).thenReturn(new MemberLevelCO("NORMAL", 10000, 100));

        pricingService.calculate(ctx(10000), order());

        ArgumentCaptor<PromotionQueryCO> captor = ArgumentCaptor.forClass(PromotionQueryCO.class);
        verify(promotionClient).queryActive(captor.capture());
        assertEquals(9L, captor.getValue().getSellerId());
    }

    // ==================== 会员（I45） ====================

    @Test
    @DisplayName("I45 降级 — fallback 返回默认 NORMAL（10000）→ 会员减免 0（fail-open 不折扣）")
    void shouldNotApplyMemberDiscount_whenMemberLevelFallsBackToNormal() {
        when(promotionClient.queryActive(any(PromotionQueryCO.class))).thenReturn(List.of());
        when(memberClient.getLevel(100L)).thenReturn(new MemberLevelCO("NORMAL", 10000, 100));

        int discount = pricingService.calculate(ctx(10000), order());

        assertEquals(0, discount);
    }

    @Test
    @DisplayName("I45 会员折扣 — GOLD 9800 → 会员减免 floor(10000×200/10000) = 200")
    void shouldApplyMemberDiscount_whenGoldLevel() {
        when(promotionClient.queryActive(any(PromotionQueryCO.class))).thenReturn(List.of());
        when(memberClient.getLevel(100L)).thenReturn(new MemberLevelCO("GOLD", 9800, 120));

        int discount = pricingService.calculate(ctx(10000), order());

        assertEquals(200, discount);
    }

    // ==================== 券（收编语义原样） ====================

    @Test
    @DisplayName("券核销语义原样 — useCoupon 收到 orderNo/orderAmount=total/orderType/sellerId/scope 串")
    void shouldKeepCouponFeignSemantics_whenCouponApplied() {
        when(promotionClient.queryActive(any(PromotionQueryCO.class))).thenReturn(List.of());
        when(memberClient.getLevel(100L)).thenReturn(new MemberLevelCO("NORMAL", 10000, 100));
        when(couponClient.useCoupon(eq(100L), eq(55L), eq("ORD-1"), eq(10000), eq(1), eq(9L),
                eq("11"), eq("22"))).thenReturn(3000);

        OrderCreateContext c = ctx(10000);
        c.setUserCouponId(55L);
        int discount = pricingService.calculate(c, order());

        assertEquals(3000, discount);
        verify(couponClient).useCoupon(eq(100L), eq(55L), eq("ORD-1"), eq(10000), eq(1), eq(9L),
                eq("11"), eq("22"));
    }

    @Test
    @DisplayName("多券逐券核销 — 第二张券 orderAmount = total − 首券已抵扣（收编语义不变）")
    void shouldPassRemainingAmountToSecondCoupon_whenMultipleCoupons() {
        when(promotionClient.queryActive(any(PromotionQueryCO.class))).thenReturn(List.of());
        when(memberClient.getLevel(100L)).thenReturn(new MemberLevelCO("NORMAL", 10000, 100));
        when(couponClient.batchGet(anyList())).thenReturn(List.of());
        when(couponClient.useCoupon(eq(100L), eq(1L), anyString(), eq(10000), anyInt(), anyLong(),
                any(), any())).thenReturn(2000);
        when(couponClient.useCoupon(eq(100L), eq(2L), anyString(), eq(8000), anyInt(), anyLong(),
                any(), any())).thenReturn(1000);

        OrderCreateContext c = ctx(10000);
        c.setUserCouponIds(List.of(1L, 2L));
        int discount = pricingService.calculate(c, order());

        assertEquals(3000, discount);
        verify(couponClient).useCoupon(eq(100L), eq(2L), eq("ORD-1"), eq(8000), anyInt(),
                anyLong(), any(), any());
    }

    @Test
    @DisplayName("互斥券校验保留 — stackRule=1 的券与其他券同用 → 抛 BizException")
    void shouldRejectStacking_whenExclusiveCouponMixed() {
        when(promotionClient.queryActive(any(PromotionQueryCO.class))).thenReturn(List.of());
        when(memberClient.getLevel(100L)).thenReturn(new MemberLevelCO("NORMAL", 10000, 100));
        org.icedamericanomall.dto.CouponStackInfoCO exclusive =
                new org.icedamericanomall.dto.CouponStackInfoCO();
        exclusive.setStackRule(1);
        when(couponClient.batchGet(anyList())).thenReturn(List.of(exclusive, exclusive));

        OrderCreateContext c = ctx(10000);
        c.setUserCouponIds(List.of(1L, 2L));

        assertThrows(BizException.class, () -> pricingService.calculate(c, order()));
        verify(couponClient, never()).useCoupon(anyLong(), anyLong(), anyString(),
                anyInt(), anyInt(), anyLong(), any(), any());
    }

    // ==================== 三源叠加 + 守卫 ====================

    @Test
    @DisplayName("三源叠加封顶 — 促销 8000 + 会员 200 + 券 1000，合计 9200 ≤ total 10000 → 9200")
    void shouldStackThreeSources_whenWithinTotal() {
        when(promotionClient.queryActive(any(PromotionQueryCO.class)))
                .thenReturn(List.of(promotion(1, 0, 8000, 0)));
        when(memberClient.getLevel(100L)).thenReturn(new MemberLevelCO("GOLD", 9800, 120));
        when(couponClient.useCoupon(anyLong(), anyLong(), anyString(), anyInt(), anyInt(),
                anyLong(), any(), any())).thenReturn(1000);

        OrderCreateContext c = ctx(10000);
        c.setUserCouponId(55L);
        int discount = pricingService.calculate(c, order());

        assertEquals(9200, discount);
    }

    @Test
    @DisplayName("总额守卫 — 促销 8000 + 会员 200 + 券 9500 合计超 total → 封顶 10000")
    void shouldCapAtTotal_whenThreeSourcesExceedTotal() {
        when(promotionClient.queryActive(any(PromotionQueryCO.class)))
                .thenReturn(List.of(promotion(1, 0, 8000, 0)));
        when(memberClient.getLevel(100L)).thenReturn(new MemberLevelCO("GOLD", 9800, 120));
        when(couponClient.useCoupon(anyLong(), anyLong(), anyString(), anyInt(), anyInt(),
                anyLong(), any(), any())).thenReturn(9500);

        OrderCreateContext c = ctx(10000);
        c.setUserCouponId(55L);
        int discount = pricingService.calculate(c, order());

        assertEquals(10000, discount);
    }

    @Test
    @DisplayName("同类型促销取最优 — I44 返回满减300与满减800 → 只取 800（不叠加）")
    void shouldPickBestPromotion_whenMultipleCandidatesReturned() {
        when(promotionClient.queryActive(any(PromotionQueryCO.class)))
                .thenReturn(List.of(promotion(1, 0, 300, 0), promotion(1, 0, 800, 0)));
        when(memberClient.getLevel(100L)).thenReturn(new MemberLevelCO("NORMAL", 10000, 100));

        int discount = pricingService.calculate(ctx(10000), order());

        assertEquals(800, discount);
    }
}
