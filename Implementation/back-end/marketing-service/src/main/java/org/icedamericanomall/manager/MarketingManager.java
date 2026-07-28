package org.icedamericanomall.manager;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.constants.GrabTypeEnum;
import org.icedamericanomall.domain.entity.CouponEntity;
import org.icedamericanomall.domain.entity.FlashSaleEntity;
import org.icedamericanomall.domain.entity.UserCouponEntity;
import org.icedamericanomall.service.CouponService;
import org.icedamericanomall.service.FlashSaleService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * V4.0 DDD: 营销编排 Manager（Application层）。
 * 职责: 秒杀+优惠券用例编排，跨Service聚合。
 */
@Component
@RequiredArgsConstructor
public class MarketingManager {

    private final FlashSaleService flashSaleService;
    private final CouponService couponService;

    /** 秒杀列表: Controller→Manager→Service */
    public List<FlashSaleEntity> listActiveFlashSales() {
        return flashSaleService.listActive();
    }

    /** 秒杀购买: Manager编排 Redis(库存)→RocketMQ(异步)→DB(落库) */
    public boolean buyFlashSale(Long flashId) {
        return flashSaleService.buy(flashId);
    }

    /**
     * V4.0: 智能优惠券领取 — 根据 grabType 自动选择领取通道。
     * - NEED_GRAB → Redis Lua 高并发通道
     * - 其他 → 普通领取通道
     */
    public UserCouponEntity smartClaim(Long userId, String couponId) {
        // 查询优惠券模板以判断 grabType
        CouponEntity coupon = couponService.lambdaQuery()
                .eq(CouponEntity::getCouponId, couponId).one();
        if (coupon == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "优惠券不存在");

        if (coupon.getGrabType() != null && coupon.getGrabType() == GrabTypeEnum.NEED_GRAB.getCode()) {
            return couponService.claimWithGrab(userId, couponId);
        }
        return couponService.claim(userId, couponId);
    }
}
