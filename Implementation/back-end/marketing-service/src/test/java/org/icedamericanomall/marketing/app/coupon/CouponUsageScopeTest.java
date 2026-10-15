package org.icedamericanomall.marketing.app.coupon;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.domain.enums.CouponCategoryEnum;
import org.icedamericanomall.marketing.domain.enums.DiscountTypeEnum;
import org.icedamericanomall.marketing.domain.enums.ScopeTypeEnum;
import org.icedamericanomall.marketing.domain.enums.UserCouponStatusEnum;
import org.icedamericanomall.marketing.domain.model.coupon.Coupon;
import org.icedamericanomall.marketing.domain.model.coupon.UserCoupon;
import org.icedamericanomall.marketing.domain.repository.CouponRepository;
import org.icedamericanomall.marketing.domain.repository.UserCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code CouponUsageManager.useCoupon} 适用范围（{@code scope_type}/{@code scope_values}）校验测试（V4.3 落地）。
 *
 * <p>口径（详见 {@code CouponScope}）：
 * <ul>
 *   <li>{@code scopeType=1 ALL} / {@code scopeType=null} / {@code scopeValues} 为空 → 放行；</li>
 *   <li>{@code 2 CATEGORY} → 订单品类与 {@code scopeValues} 有交集才放行；</li>
 *   <li>{@code 3 PRODUCT} → 订单商品与 {@code scopeValues} 有交集才放行；</li>
 *   <li>有限定范围但调用方未传 ids → <b>拒绝</b>（漏传不等于不限）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponUsageManager.useCoupon — 适用范围校验（V4.3）")
class CouponUsageScopeTest {

    private static final Long USER_ID = 100L;
    private static final Long USER_COUPON_ID = 55L;
    private static final Long TEMPLATE_ID = 9L;
    private static final String ORDER_NO = "ORD-0001";
    private static final int ORDER_AMOUNT = 10000;

    /** 订单商品快照（PricingService.buildProductIds/buildCategoryIds 的产物口径）。 */
    private static final String ORDER_PRODUCT_IDS = "1001,1002";
    private static final String ORDER_CATEGORY_IDS = "7,8";

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    private final MarketingConverter converter = Mappers.getMapper(MarketingConverter.class);

    private CouponUsageManager couponUsageManager;

    @BeforeEach
    void setUp() {
        couponUsageManager = new CouponUsageManager(couponRepository, userCouponRepository, converter);
    }

    // ==================== 放行路径 ====================

    @Test
    @DisplayName("useCoupon — scopeType=1 ALL 全场 → 放行并核销")
    void shouldApply_whenScopeTypeAll() {
        int discount = useCoupon(coupon(ScopeTypeEnum.ALL.getCode(), "[\"7\",\"8\"]"), ORDER_PRODUCT_IDS, ORDER_CATEGORY_IDS);

        assertEquals(1000, discount);
        verify(userCouponRepository).update(org.mockito.ArgumentMatchers.any(UserCoupon.class));
    }

    @Test
    @DisplayName("useCoupon — scopeType=null（存量/兼容）→ 视为 ALL 放行")
    void shouldApply_whenScopeTypeNull() {
        int discount = useCoupon(coupon(null, "[\"7\"]"), ORDER_PRODUCT_IDS, ORDER_CATEGORY_IDS);

        assertEquals(1000, discount);
    }

    @Test
    @DisplayName("useCoupon — scopeValues 为空 → 视为 ALL 放行（不因缺配置误伤）")
    void shouldApply_whenScopeValuesBlank() {
        int discount = useCoupon(coupon(ScopeTypeEnum.PRODUCT.getCode(), "  "), null, null);

        assertEquals(1000, discount);
    }

    @Test
    @DisplayName("useCoupon — scopeType=2 CATEGORY 且订单品类命中 → 放行")
    void shouldApply_whenCategoryScopeMatched() {
        int discount = useCoupon(coupon(ScopeTypeEnum.CATEGORY.getCode(), "[\"7\",\"9\"]"),
                ORDER_PRODUCT_IDS, ORDER_CATEGORY_IDS);

        assertEquals(1000, discount);
    }

    @Test
    @DisplayName("useCoupon — scopeType=2 CATEGORY 数字形态 JSON [7,8] 同样命中 → 放行")
    void shouldApply_whenCategoryScopeMatchedWithNumericJson() {
        int discount = useCoupon(coupon(ScopeTypeEnum.CATEGORY.getCode(), "[7,8]"),
                ORDER_PRODUCT_IDS, ORDER_CATEGORY_IDS);

        assertEquals(1000, discount);
    }

    @Test
    @DisplayName("useCoupon — scopeType=3 PRODUCT 且订单商品命中 → 放行")
    void shouldApply_whenProductScopeMatched() {
        int discount = useCoupon(coupon(ScopeTypeEnum.PRODUCT.getCode(), "[\"1002\"]"),
                ORDER_PRODUCT_IDS, ORDER_CATEGORY_IDS);

        assertEquals(1000, discount);
    }

    // ==================== 拒绝路径 ====================

    @Test
    @DisplayName("useCoupon — scopeType=2 CATEGORY 但订单品类无交集 → BizException 且不核销")
    void shouldReject_whenCategoryScopeNotMatched() {
        stubCoupon(coupon(ScopeTypeEnum.CATEGORY.getCode(), "[\"1\",\"2\"]"));

        BizException ex = assertThrows(BizException.class,
                () -> couponUsageManager.useCoupon(USER_ID, USER_COUPON_ID, ORDER_NO, ORDER_AMOUNT,
                        1, null, ORDER_PRODUCT_IDS, ORDER_CATEGORY_IDS));

        assertEquals(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, ex.getErrorCode());
        assertEquals("该优惠券不适用于当前订单商品", ex.getMessage());
        verify(userCouponRepository, never()).update(org.mockito.ArgumentMatchers.any(UserCoupon.class));
    }

    @Test
    @DisplayName("useCoupon — scopeType=3 PRODUCT 但订单商品无交集 → BizException 且不核销")
    void shouldReject_whenProductScopeNotMatched() {
        stubCoupon(coupon(ScopeTypeEnum.PRODUCT.getCode(), "[\"9999\"]"));

        BizException ex = assertThrows(BizException.class,
                () -> couponUsageManager.useCoupon(USER_ID, USER_COUPON_ID, ORDER_NO, ORDER_AMOUNT,
                        1, null, ORDER_PRODUCT_IDS, ORDER_CATEGORY_IDS));

        assertEquals(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, ex.getErrorCode());
        assertEquals("该优惠券不适用于当前订单商品", ex.getMessage());
        verify(userCouponRepository, never()).update(org.mockito.ArgumentMatchers.any(UserCoupon.class));
    }

    @Test
    @DisplayName("useCoupon — 限定品类但调用方未传 categoryIds → 拒绝（漏传不等于不限）")
    void shouldReject_whenCategoryScopeLimitedButCategoryIdsMissing() {
        stubCoupon(coupon(ScopeTypeEnum.CATEGORY.getCode(), "[\"7\",\"8\"]"));

        BizException ex = assertThrows(BizException.class,
                () -> couponUsageManager.useCoupon(USER_ID, USER_COUPON_ID, ORDER_NO, ORDER_AMOUNT,
                        1, null, ORDER_PRODUCT_IDS, null));

        assertEquals("该优惠券不适用于当前订单商品", ex.getMessage());
    }

    @Test
    @DisplayName("useCoupon — 限定单品但调用方未传 productIds → 拒绝（漏传不等于不限）")
    void shouldReject_whenProductScopeLimitedButProductIdsMissing() {
        stubCoupon(coupon(ScopeTypeEnum.PRODUCT.getCode(), "[\"1001\"]"));

        BizException ex = assertThrows(BizException.class,
                () -> couponUsageManager.useCoupon(USER_ID, USER_COUPON_ID, ORDER_NO, ORDER_AMOUNT,
                        1, null, null, ORDER_CATEGORY_IDS));

        assertEquals("该优惠券不适用于当前订单商品", ex.getMessage());
    }

    // ==================== 夹具 ====================

    /** 以指定适用范围执行核销（券类别固定 PLATFORM、门槛已满足）。 */
    private int useCoupon(Coupon coupon, String productIds, String categoryIds) {
        stubCoupon(coupon);
        return couponUsageManager.useCoupon(USER_ID, USER_COUPON_ID, ORDER_NO, ORDER_AMOUNT,
                1, null, productIds, categoryIds);
    }

    /** 持有可用券 + 指定券模板。 */
    private void stubCoupon(Coupon coupon) {
        when(userCouponRepository.findById(USER_COUPON_ID)).thenReturn(unusedUserCoupon());
        when(couponRepository.findById(TEMPLATE_ID)).thenReturn(coupon);
    }

    private static Coupon coupon(Integer scopeType, String scopeValues) {
        return Coupon.builder()
                .id(TEMPLATE_ID)
                .couponId("0f1e2d3c4b5a69788796a5b4c3d2e1f0")
                .name("范围测试券")
                .value(1000)
                .minAmount(1000)
                .status(1)
                .discountType(DiscountTypeEnum.FIXED.getCode())
                .couponCategory(CouponCategoryEnum.PLATFORM.getCode())
                .scopeType(scopeType)
                .scopeValues(scopeValues)
                .build();
    }

    private static UserCoupon unusedUserCoupon() {
        return UserCoupon.builder()
                .id(USER_COUPON_ID)
                .userId(USER_ID)
                .couponId(TEMPLATE_ID)
                .status(UserCouponStatusEnum.UNUSED.getCode())
                .build();
    }
}
