package org.icedamericanomall.marketing.app.coupon;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.adapter.response.UserCouponVO;
import org.icedamericanomall.marketing.domain.enums.CouponCategoryEnum;
import org.icedamericanomall.marketing.domain.enums.OrderTypeEnum;
import org.icedamericanomall.marketing.domain.model.coupon.Coupon;
import org.icedamericanomall.marketing.domain.model.coupon.UserCoupon;
import org.icedamericanomall.marketing.domain.repository.CouponRepository;
import org.icedamericanomall.marketing.domain.repository.UserCouponRepository;
import org.icedamericanomall.marketing.domain.service.CouponPricing;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 优惠券核销用例族编排（应用层 · 下单核销/回滚/持券查询）。
 *
 * <p>折扣口径统一委托纯领域服务 {@link CouponPricing}；类别/店铺适用性校验内聚在
 * {@link #useCoupon}（秒杀订单拒绝用券，店铺券限本店）。
 */
@Component
@RequiredArgsConstructor
public class CouponUsageManager {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final MarketingConverter marketingConverter;

    /** 未使用券列表（向后兼容口径）。 */
    public List<UserCouponVO> listAvailableCoupons(Long userId) {
        return marketingConverter.toUserCouponVOList(userCouponRepository.findUnusedByUserId(userId));
    }

    /** 已使用/已过期券列表（按使用时间倒序）。 */
    public List<UserCouponVO> listUsedCoupons(Long userId) {
        return marketingConverter.toUserCouponVOList(userCouponRepository.findUsedByUserId(userId));
    }

    /**
     * 使用优惠券（下单时调用），支持 discountType 三维度与 couponCategory 适用范围校验。
     *
     * <p>类别规则：PLATFORM 通用；SHOP 限本店订单；FLASH_SALE 券仅限秒杀订单；而秒杀订单
     * （{@code orderType=FLASH_SALE}）本身拒绝所有优惠券（秒杀价已是底价，后端兜底）。
     *
     * @return 实际抵扣金额（分）
     */
    @Transactional(rollbackFor = Exception.class)
    public int useCoupon(Long userId, Long userCouponId, String orderNo, int orderAmount,
                         Integer orderType, Long sellerId) {
        UserCoupon uc = userCouponRepository.findById(userCouponId);
        if (uc == null || !uc.isOwnedBy(userId)) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券不存在");
        }
        if (!uc.isUnused()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券不可用");
        }

        Coupon coupon = couponRepository.findById(uc.getCouponId());
        if (coupon == null) {
            throw new BizException(ErrorCode.COUPON_NOT_FOUND, "优惠券模板已删除");
        }
        if (orderAmount < coupon.getMinAmount()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "未达到最低消费金额");
        }

        int category = coupon.getCouponCategory() != null ? coupon.getCouponCategory()
                : (coupon.getSellerId() == null ? CouponCategoryEnum.PLATFORM.getCode()
                                                : CouponCategoryEnum.SHOP.getCode());
        if (orderType != null) {
            if (orderType == OrderTypeEnum.FLASH_SALE.getCode()) {
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "秒杀订单不支持使用优惠券");
            }
            // FLASH_SALE 券仅限秒杀订单 → 但秒杀订单已在上方拒绝，此处为语义完整性
            if (category == CouponCategoryEnum.FLASH_SALE.getCode()) {
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "秒杀券仅限秒杀订单使用");
            }
        }
        if (category == CouponCategoryEnum.SHOP.getCode()) {
            if (coupon.getSellerId() == null || !coupon.getSellerId().equals(sellerId)) {
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "店铺券仅限该店铺订单使用");
            }
        }

        int discountType = CouponPricing.resolveDiscountType(coupon.getDiscountType(), coupon.getType());
        int discount = CouponPricing.calculate(discountType, coupon.getValue(),
                coupon.getMinAmount(), orderAmount);

        uc.markUsed(orderNo);
        userCouponRepository.update(uc);
        return discount;
    }

    /** 按持券 ID 回滚（已使用才回滚，幂等）。 */
    @Transactional(rollbackFor = Exception.class)
    public void rollbackCoupon(Long userCouponId) {
        UserCoupon uc = userCouponRepository.findById(userCouponId);
        if (uc != null && uc.isUsed()) {
            restore(uc);
        }
    }

    /** 按订单号回滚优惠券（订单取消/超时补偿）。 */
    @Transactional(rollbackFor = Exception.class)
    public void rollbackByOrderNo(String orderNo) {
        UserCoupon uc = userCouponRepository.findUsedByOrderNo(orderNo);
        if (uc != null) {
            restore(uc);
        }
    }

    /** 回滚一张已使用的券为可用。 */
    private void restore(UserCoupon uc) {
        uc.restore();
        userCouponRepository.update(uc);
    }
}
