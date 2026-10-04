package org.icedamericanomall.marketing.domain.service;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.domain.enums.DiscountTypeEnum;

/**
 * 优惠券折扣计算（纯领域服务）—— 无框架依赖、无状态、可脱离 Spring 直接单测。
 *
 * <p>唯一的折扣口径来源：优惠券服务（下单抵扣）与优惠券持久化对象 {@code CouponPO}
 * 的 {@code calculateDiscount} 均委托本类，避免同一算法在多处复制后失配（此前的重复实现
 * 就以 “未达门槛” 与 “未知类型” 两种分支语义不一致）。
 *
 * <pre>
 * 规则:
 *   1. 订单金额未达 minAmount → 抵扣 0
 *   2. FIXED(满减) / CASH_COUPON(代金券) → 抵扣面值
 *   3. PERCENTAGE(折扣) → value 为折后百分比（value=80 即打 8 折，抵扣 20%）
 *   4. 抵扣金额封顶为订单金额（不允许负支付）
 * </pre>
 */
public final class CouponPricing {

    /** 百分比基数（100 = 不打折）。 */
    private static final int PERCENT_BASE = 100;

    private CouponPricing() {
    }

    /**
     * 解析折扣类型：优先新字段 {@code discountType}，为空回退历史字段 {@code type}。
     *
     * @param discountType 五维模型折扣类型（可空）
     * @param legacyType   历史 type 字段（可空）
     * @return 生效的折扣类型码，两者皆空时按满减券处理
     */
    public static int resolveDiscountType(Integer discountType, Integer legacyType) {
        if (discountType != null) {
            return discountType;
        }
        return legacyType != null ? legacyType : DiscountTypeEnum.FIXED.getCode();
    }

    /**
     * 计算抵扣金额（单位：分）。
     *
     * @param discountType 折扣类型码（见 {@link DiscountTypeEnum}）
     * @param value        面值（满减/代金券为金额，折扣券为折后百分比）
     * @param minAmount    最低消费门槛（分，可空）
     * @param orderAmount  订单金额（分）
     * @return 实际抵扣金额（分），范围 [0, orderAmount]
     * @throws BizException 折扣类型未知时抛出（错误码 {@code BUSINESS_EXECUTION_EXCEPTION}）
     */
    public static int calculate(int discountType, Integer value, Integer minAmount, int orderAmount) {
        if (minAmount != null && minAmount > 0 && orderAmount < minAmount) {
            return 0;
        }
        int faceValue = value != null ? value : 0;
        int discount;
        if (discountType == DiscountTypeEnum.FIXED.getCode()) {
            discount = faceValue;
        } else if (discountType == DiscountTypeEnum.PERCENTAGE.getCode()) {
            discount = orderAmount * (PERCENT_BASE - faceValue) / PERCENT_BASE;
        } else if (discountType == DiscountTypeEnum.CASH_COUPON.getCode()) {
            discount = faceValue;
        } else {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "未知的优惠券类型: " + discountType);
        }
        return Math.max(0, Math.min(discount, orderAmount));
    }
}
