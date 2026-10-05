package org.icedamericanomall.trade.domain.service;

import org.icedamericanomall.trade.domain.service.PricingCalculator.PromotionRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link PricingCalculator} 价格计算纯函数单元测试 —— <b>逐条锁定 ADR-043 计价口径</b>
 * （门槛边界 / 满折封顶 / 取最优 / 三源叠加 / 总额守卫 / 零优惠）。
 *
 * <p>纯函数不依赖任何框架，直接构造用例对象调用，无需 Spring / Mock。
 * 金额一律「分」，万分比/百分比除法为整数除法（向下取整）。
 */
@DisplayName("PricingCalculator — 价格计算纯函数（ADR-043 计价口径）")
class PricingCalculatorTest {

    /** 满减活动便捷构造。 */
    private static PromotionRule fixed(Integer threshold, int discountValue) {
        return new PromotionRule(PricingCalculator.PROMO_TYPE_FIXED, threshold, discountValue, 0);
    }

    /** 满折活动便捷构造（discountValue 万分比）。 */
    private static PromotionRule percent(Integer threshold, int discountValue, int maxDiscount) {
        return new PromotionRule(PricingCalculator.PROMO_TYPE_PERCENTAGE, threshold, discountValue,
                maxDiscount);
    }

    // ==================== 口径 2/3：满减 + 门槛边界 ====================

    @Test
    @DisplayName("门槛边界 — totalAmount = threshold → 生效（≥ 而非 >）")
    void shouldApplyPromotion_whenTotalEqualsThreshold() {
        // Given 满减 10001 减 1000，总额 10001（恰好压线）
        int reduction = PricingCalculator.bestPromotionDiscount(
                List.of(fixed(10001, 1000)), 10001);

        assertEquals(1000, reduction);
    }

    @Test
    @DisplayName("门槛未命中 — totalAmount < threshold → 不参与计价，减免 0")
    void shouldReturnZero_whenTotalBelowThreshold() {
        assertEquals(0, PricingCalculator.bestPromotionDiscount(List.of(fixed(10000, 1000)), 9999));
    }

    @Test
    @DisplayName("threshold 缺失 → 视为恒生效")
    void shouldApplyPromotion_whenThresholdIsNull() {
        assertEquals(500, PricingCalculator.bestPromotionDiscount(
                List.of(fixed(null, 500)), 1));
    }

    // ==================== 口径 4：满折万分比 + 封顶 ====================

    @Test
    @DisplayName("满折 — 8500（8.5折）× 总额 9999 → 减免 floor(9999×1500/10000) = 1499（整数除法向下取整）")
    void shouldFloorPercentageReduction() {
        int reduction = PricingCalculator.bestPromotionDiscount(
                List.of(percent(0, 8500, 0)), 9999);

        assertEquals(1499, reduction);
    }

    @Test
    @DisplayName("满折封顶 — 计算减免超 maxDiscount → 取 maxDiscount")
    void shouldCapPercentageReductionAtMaxDiscount() {
        // 总额 100000 × 15% = 15000，封顶 8000
        int reduction = PricingCalculator.bestPromotionDiscount(
                List.of(percent(0, 8500, 8000)), 100000);

        assertEquals(8000, reduction);
    }

    @Test
    @DisplayName("满折不封顶 — maxDiscount=0 → 按计算值全额减免")
    void shouldNotCap_whenMaxDiscountIsZero() {
        assertEquals(15000, PricingCalculator.bestPromotionDiscount(
                List.of(percent(0, 8500, 0)), 100000));
    }

    @Test
    @DisplayName("满折无优惠 — discountValue = 10000（万分比满额）→ 减免 0")
    void shouldReturnZero_whenPercentageDiscountValueIsFull() {
        assertEquals(0, PricingCalculator.bestPromotionDiscount(
                List.of(percent(0, 10000, 0)), 5000));
    }

    // ==================== 口径 2：同类型/跨类型取减免最大者（不叠加） ====================

    @Test
    @DisplayName("取最优 — 两个满减（减300 / 减800）都达门槛 → 取减免最大 800（不叠加）")
    void shouldPickLargestReduction_whenMultipleFixedPromotions() {
        int reduction = PricingCalculator.bestPromotionDiscount(
                List.of(fixed(0, 300), fixed(0, 800)), 10000);

        assertEquals(800, reduction);
    }

    @Test
    @DisplayName("取最优 — 满减(减500) vs 满折(8.5折=减1500) → 取满折 1500")
    void shouldPickBestAcrossTypes_whenFixedAndPercentageBothApply() {
        int reduction = PricingCalculator.bestPromotionDiscount(
                List.of(fixed(0, 500), percent(0, 8500, 0)), 10000);

        assertEquals(1500, reduction);
    }

    @Test
    @DisplayName("取最优 — 减免最大者门槛未命中时被排除，其余候选中取最大")
    void shouldExcludeBelowThresholdCandidates_whenPickingBest() {
        // 满减 2000 需总额 ≥ 20000（未达）；满减 800 恒生效 → 取 800
        int reduction = PricingCalculator.bestPromotionDiscount(
                List.of(fixed(20000, 2000), fixed(0, 800)), 10000);

        assertEquals(800, reduction);
    }

    // ==================== 口径 5：会员折扣 ====================

    @Test
    @DisplayName("会员折扣 — discountRate = 10000（不折扣）→ 减免 0")
    void shouldReturnZeroMemberDiscount_whenRateIsFull() {
        assertEquals(0, PricingCalculator.memberDiscount(10000, 10000));
    }

    @Test
    @DisplayName("会员折扣 — GOLD 9800（98折）× 总额 9999 → floor(9999×200/10000) = 199")
    void shouldFloorMemberDiscount() {
        assertEquals(199, PricingCalculator.memberDiscount(9999, 9800));
    }

    @Test
    @DisplayName("会员折扣 — discountRate 缺失（I45 降级）→ 减免 0")
    void shouldReturnZeroMemberDiscount_whenRateIsNull() {
        assertEquals(0, PricingCalculator.memberDiscount(10000, null));
    }

    @Test
    @DisplayName("会员折扣异常配置 — discountRate ≤ 0 → 减免封顶 totalAmount（不产生负 payAmount）")
    void shouldCapMemberDiscountAtTotal_whenRateInvalid() {
        assertEquals(5000, PricingCalculator.memberDiscount(5000, 0));
    }

    // ==================== 口径 1/6：三源叠加 + 总额守卫 ====================

    @Test
    @DisplayName("三源叠加 — 促销 800 + 会员 200 + 券 1000，合计 ≤ total → 原样合计 2000")
    void shouldSumThreeSources_whenWithinTotal() {
        int discount = PricingCalculator.totalDiscount(10000, 800, 200, 1000);

        assertEquals(2000, discount);
    }

    @Test
    @DisplayName("总额守卫 — 三源合计 12000 > total 10000 → 封顶 10000（payAmount 恒 ≥ 0）")
    void shouldCapAtTotal_whenThreeSourcesExceedTotal() {
        int discount = PricingCalculator.totalDiscount(10000, 8000, 2000, 1000);

        assertEquals(10000, discount);
    }

    @Test
    @DisplayName("零优惠 — 三源全 0 → discountAmount 0")
    void shouldReturnZero_whenNoDiscountSource() {
        assertEquals(0, PricingCalculator.totalDiscount(10000, 0, 0, 0));
    }

    @Test
    @DisplayName("守卫防御 — 单源负值被钳为 0，不拉低合计")
    void shouldClampNegativeSourceToZero() {
        assertEquals(1000, PricingCalculator.totalDiscount(10000, -500, 1000, -200));
    }

    // ==================== 空集合 / 未知类型 / null 防御 ====================

    @Test
    @DisplayName("无生效活动 — 空集合 / null → 促销减免 0（合法业务态，非降级）")
    void shouldReturnZero_whenNoActivePromotions() {
        assertEquals(0, PricingCalculator.bestPromotionDiscount(List.of(), 10000));
        assertEquals(0, PricingCalculator.bestPromotionDiscount(null, 10000));
    }

    @Test
    @DisplayName("未知促销类型（null/其他）与 null 规则 → 跳过不计价")
    void shouldSkipUnknownTypeAndNullRules() {
        PromotionRule unknownType = new PromotionRule(9, 0, 1000, 0);
        assertEquals(0, PricingCalculator.bestPromotionDiscount(List.of(unknownType), 10000));
        assertEquals(0, PricingCalculator.bestPromotionDiscount(
                List.of(new PromotionRule(null, 0, 1000, 0)), 10000));
        assertEquals(0, PricingCalculator.bestPromotionDiscount(
                java.util.Collections.singletonList(null), 10000));
    }
}
