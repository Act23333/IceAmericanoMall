package org.icedamericanomall.marketing.app.coupon;

import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.domain.enums.UserCouponStatusEnum;
import org.icedamericanomall.marketing.domain.repository.CouponRepository;
import org.icedamericanomall.marketing.domain.repository.UserCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

/**
 * CouponUsageManager 持券计数单元测试（V5.11 M7 我的券三态计数）。
 *
 * <p>覆盖：三态计数键值口径（available/used/expired 对应 status 1/2/3）、
 * 无持券时全零（合法业务态）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponUsageManager — M7 三态计数单元测试")
class CouponUsageManagerTest {

    private static final Long USER_ID = 9L;

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

    @Test
    @DisplayName("count — 三态各自计数：available=未使用 / used=已使用 / expired=已过期")
    void shouldCountThreeStates_whenUserHasCoupons() {
        when(userCouponRepository.countByUserIdAndStatus(eq(USER_ID), eq(UserCouponStatusEnum.UNUSED)))
                .thenReturn(2L);
        when(userCouponRepository.countByUserIdAndStatus(eq(USER_ID), eq(UserCouponStatusEnum.USED)))
                .thenReturn(1L);
        when(userCouponRepository.countByUserIdAndStatus(eq(USER_ID), eq(UserCouponStatusEnum.EXPIRED)))
                .thenReturn(3L);

        Map<String, Integer> counts = couponUsageManager.countMyCoupons(USER_ID);

        assertEquals(3, counts.size());
        assertEquals(2, counts.get("available"));
        assertEquals(1, counts.get("used"));
        assertEquals(3, counts.get("expired"));
    }

    @Test
    @DisplayName("count — 用户无持券 → 三态全零（合法业务态）")
    void shouldReturnZeros_whenUserHasNoCoupons() {
        Map<String, Integer> counts = couponUsageManager.countMyCoupons(USER_ID);

        assertEquals(3, counts.size());
        assertTrue(counts.values().stream().allMatch(v -> v == 0));
    }
}
