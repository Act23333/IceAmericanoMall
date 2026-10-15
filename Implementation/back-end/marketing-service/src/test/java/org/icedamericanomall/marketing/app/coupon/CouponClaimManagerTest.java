package org.icedamericanomall.marketing.app.coupon;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverterImpl;
import org.icedamericanomall.marketing.domain.enums.CouponStatusEnum;
import org.icedamericanomall.marketing.domain.enums.GrabTypeEnum;
import org.icedamericanomall.marketing.domain.model.coupon.Coupon;
import org.icedamericanomall.marketing.domain.port.CouponClaimDedupStore;
import org.icedamericanomall.marketing.domain.port.CouponGrabStore;
import org.icedamericanomall.marketing.domain.repository.CouponRepository;
import org.icedamericanomall.marketing.domain.repository.UserCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 秒杀级优惠券领取（claimWithGrab）单元测试 —— 重点覆盖 Redis 补偿契约。
 *
 * <p>契约：Redis 预扣成功后 DB 落库失败，必须 INCR 回滚库存 + SREM 撤销去重标记，
 * 否则名额永久缺失（Redis 扣了、DB 没有）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponClaimManager 抢券补偿单元测试")
class CouponClaimManagerTest {

    private static final Long USER_ID = 7L;
    private static final String COUPON_ID = "CPN-001";
    private static final Long COUPON_PK = 1L;

    @Mock CouponRepository couponRepository;
    @Mock UserCouponRepository userCouponRepository;
    @Mock CouponGrabStore couponGrabStore;
    @Mock CouponClaimDedupStore couponClaimDedupStore;

    private CouponClaimManager couponClaimManager;

    @BeforeEach
    void setUp() {
        couponClaimManager = new CouponClaimManager(couponRepository, userCouponRepository,
                couponClaimDedupStore, couponGrabStore,
                new UserCouponViewAssembler(new MarketingConverterImpl(), couponIds -> java.util.Map.of()));
    }

    @Test
    @DisplayName("claimWithGrab — 券不存在 → COUPON_NOT_FOUND，不触碰 Redis")
    void shouldThrowCouponNotFound_whenCouponMissing() {
        when(couponRepository.findByCouponId(COUPON_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> couponClaimManager.claimWithGrab(USER_ID, COUPON_ID));

        assertEquals(ErrorCode.COUPON_NOT_FOUND, ex.getErrorCode());
        verifyNoInteractions(couponGrabStore);
    }

    @Test
    @DisplayName("claimWithGrab — DB 落库失败 → 回滚 Redis 预扣并抛业务异常")
    void shouldRollbackRedisAndThrow_whenDbInsertFails() {
        when(couponRepository.findByCouponId(COUPON_ID)).thenReturn(grabCoupon());
        when(couponGrabStore.tryClaim(anyLong(), anyLong(), any())).thenReturn(4L);
        when(userCouponRepository.save(any())).thenThrow(new IllegalStateException("db down"));

        BizException ex = assertThrows(BizException.class,
                () -> couponClaimManager.claimWithGrab(USER_ID, COUPON_ID));

        assertEquals(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, ex.getErrorCode());
        assertEquals("优惠券领取失败，请重试", ex.getDisplayMessage());
        verify(couponGrabStore).rollbackClaim(COUPON_PK, USER_ID);
    }

    @Test
    @DisplayName("claimWithGrab — DB 落库成功 → 不回滚 Redis，返回持券记录（含券面三字段）")
    void shouldNotRollbackRedis_whenDbInsertSucceeds() {
        when(couponRepository.findByCouponId(COUPON_ID)).thenReturn(grabCoupon());
        when(couponGrabStore.tryClaim(anyLong(), anyLong(), any())).thenReturn(4L);
        when(userCouponRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = couponClaimManager.claimWithGrab(USER_ID, COUPON_ID);

        assertNotNull(result);
        assertEquals(1, result.getStatus().intValue());
        // 契约 §4.10.1：领取回执与「我的券」是同一个 VO —— 券面三字段必须同样在场
        assertEquals("秒杀券", result.getName());
        assertEquals(Integer.valueOf(3000), result.getValue());
        assertEquals(Integer.valueOf(20000), result.getMinAmount());
        verify(couponGrabStore, never()).rollbackClaim(anyLong(), anyLong());
    }

    @Test
    @DisplayName("claimWithGrab — Redis 已抢光 → 抛已抢光且不落库、不回滚")
    void shouldThrowSoldOut_whenRedisSoldOut() {
        when(couponRepository.findByCouponId(COUPON_ID)).thenReturn(grabCoupon());
        when(couponGrabStore.tryClaim(anyLong(), anyLong(), any()))
                .thenReturn(CouponGrabStore.SOLD_OUT);

        BizException ex = assertThrows(BizException.class,
                () -> couponClaimManager.claimWithGrab(USER_ID, COUPON_ID));

        assertEquals(ErrorCode.FLASH_SALE_SOLD_OUT, ex.getErrorCode());
        verify(userCouponRepository, never()).save(any());
        verify(couponGrabStore, never()).rollbackClaim(anyLong(), anyLong());
    }

    @Test
    @DisplayName("claimWithGrab — 已领取过 → 抛限购异常且不落库")
    void shouldThrowDuplicate_whenAlreadyClaimed() {
        when(couponRepository.findByCouponId(COUPON_ID)).thenReturn(grabCoupon());
        when(couponGrabStore.tryClaim(anyLong(), anyLong(), any()))
                .thenReturn(CouponGrabStore.DUPLICATE);

        BizException ex = assertThrows(BizException.class,
                () -> couponClaimManager.claimWithGrab(USER_ID, COUPON_ID));

        assertEquals(ErrorCode.FLASH_SALE_LIMIT_EXCEEDED, ex.getErrorCode());
        verify(userCouponRepository, never()).save(any());
    }

    /** 一张可抢的秒杀券模板（已发布、grabType=NEED_GRAB、在有效期内）。 */
    private Coupon grabCoupon() {
        return Coupon.builder()
                .id(COUPON_PK)
                .couponId(COUPON_ID)
                .name("秒杀券")
                .value(3000)
                .minAmount(20000)
                .status(CouponStatusEnum.ACTIVE.getCode())
                .grabType(GrabTypeEnum.NEED_GRAB.getCode())
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now().plusDays(3))
                .build();
    }
}
