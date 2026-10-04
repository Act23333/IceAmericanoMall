package org.icedamericanomall.marketing.domain.service;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.domain.enums.DiscountTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CouponPricing 纯领域服务单元测试 —— 满减/折扣/代金券三种口径与门槛边界。
 */
@DisplayName("CouponPricing 折扣计算领域单元测试")
class CouponPricingTest {

    private static final int FIXED = DiscountTypeEnum.FIXED.getCode();
    private static final int PERCENTAGE = DiscountTypeEnum.PERCENTAGE.getCode();
    private static final int CASH_COUPON = DiscountTypeEnum.CASH_COUPON.getCode();

    @Test
    @DisplayName("calculate — 满减券：抵扣面值")
    void shouldDeductFaceValue_whenFixedType() {
        assertEquals(1000, CouponPricing.calculate(FIXED, 1000, null, 5000));
    }

    @Test
    @DisplayName("calculate — 未达最低消费门槛 → 抵扣 0")
    void shouldReturnZero_whenOrderBelowMinAmount() {
        assertEquals(0, CouponPricing.calculate(FIXED, 1000, 5000, 4999));
    }

    @Test
    @DisplayName("calculate — 折扣券：value=折后百分比（80 即打 8 折，抵扣 20%）")
    void shouldDeductPercentage_whenDiscountType() {
        assertEquals(2000, CouponPricing.calculate(PERCENTAGE, 80, null, 10000));
    }

    @Test
    @DisplayName("calculate — 代金券：抵扣面值且封顶订单金额")
    void shouldCapAtOrderAmount_whenCashCouponExceedsOrder() {
        assertEquals(3000, CouponPricing.calculate(CASH_COUPON, 5000, null, 3000));
    }

    @Test
    @DisplayName("calculate — 满减面值超过订单金额 → 封顶为订单金额")
    void shouldCapAtOrderAmount_whenFixedExceedsOrder() {
        assertEquals(1200, CouponPricing.calculate(FIXED, 9900, null, 1200));
    }

    @Test
    @DisplayName("calculate — 面值为空 → 抵扣 0")
    void shouldReturnZero_whenValueNull() {
        assertEquals(0, CouponPricing.calculate(FIXED, null, null, 5000));
    }

    @Test
    @DisplayName("calculate — 折扣百分比超过 100 → 抵扣 0（不允许负支付）")
    void shouldReturnZero_whenPercentageOverHundred() {
        assertEquals(0, CouponPricing.calculate(PERCENTAGE, 150, null, 10000));
    }

    @Test
    @DisplayName("calculate — 未知折扣类型 → 抛业务异常")
    void shouldThrow_whenDiscountTypeUnknown() {
        BizException ex = assertThrows(BizException.class,
                () -> CouponPricing.calculate(99, 1000, null, 5000));
        assertEquals(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, ex.getErrorCode());
    }

    @Test
    @DisplayName("resolveDiscountType — discountType 为空回退历史 type 字段")
    void shouldFallbackToLegacyType_whenDiscountTypeNull() {
        assertEquals(PERCENTAGE, CouponPricing.resolveDiscountType(null, PERCENTAGE));
        assertEquals(FIXED, CouponPricing.resolveDiscountType(FIXED, PERCENTAGE));
    }

    @Test
    @DisplayName("resolveDiscountType — 两者皆空 → 按满减券处理")
    void shouldDefaultToFixed_whenBothNull() {
        assertEquals(FIXED, CouponPricing.resolveDiscountType(null, null));
    }
}
