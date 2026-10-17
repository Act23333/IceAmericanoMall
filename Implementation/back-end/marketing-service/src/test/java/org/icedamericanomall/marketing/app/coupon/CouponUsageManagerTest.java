package org.icedamericanomall.marketing.app.coupon;

import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.adapter.response.UserCouponCountVO;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

/**
 * CouponUsageManager 持券计数单元测试（V5.11 M7 我的券三态计数）。
 *
 * <p>覆盖：三态计数口径（available/used/expired 对应 status 1/2/3）、
 * 无持券时全零（合法业务态）。
 *
 * <p><b>批次 D（{@code Result<Map>} → 类型化 VO）</b>：返回值由
 * {@code Map<String, Integer>} 改为 {@link UserCouponCountVO}，断言由
 * {@code counts.get("available")} 改为 {@code counts.getAvailable()} ——
 * <b>线上键未变</b>（三个单字键），故 JSON 断言面零改动。
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
        // 券面读模型端口：本测试不覆盖券面回填链路（真实 SQL 语义见 CouponFaceQueryImplH2Test）
        couponUsageManager = new CouponUsageManager(couponRepository, userCouponRepository,
                new UserCouponViewAssembler(converter, couponIds -> java.util.Map.of()));
    }

    @Test
    @DisplayName("count — 三态各自计数：available=未使用 / used=已使用 / expired=已过期（批次 D：类型化 VO）")
    void shouldCountThreeStates_whenUserHasCoupons() {
        when(userCouponRepository.countByUserIdAndStatus(eq(USER_ID), eq(UserCouponStatusEnum.UNUSED)))
                .thenReturn(2L);
        when(userCouponRepository.countByUserIdAndStatus(eq(USER_ID), eq(UserCouponStatusEnum.USED)))
                .thenReturn(1L);
        when(userCouponRepository.countByUserIdAndStatus(eq(USER_ID), eq(UserCouponStatusEnum.EXPIRED)))
                .thenReturn(3L);

        UserCouponCountVO counts = couponUsageManager.countMyCoupons(USER_ID);

        assertEquals(2, counts.getAvailable());
        assertEquals(1, counts.getUsed());
        assertEquals(3, counts.getExpired());
    }

    @Test
    @DisplayName("count — 用户无持券 → 三态全零（合法业务态）")
    void shouldReturnZeros_whenUserHasNoCoupons() {
        UserCouponCountVO counts = couponUsageManager.countMyCoupons(USER_ID);

        assertEquals(0, counts.getAvailable());
        assertEquals(0, counts.getUsed());
        assertEquals(0, counts.getExpired());
    }
}
