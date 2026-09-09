package org.icedamericanomall.constants;

import lombok.Getter;

/**
 * 大厂标准: 优惠券类型枚举（京东/阿里优惠券类型体系）
 *
 * @deprecated V4.0: 请使用 {@link DiscountTypeEnum} 代替。
 *             优惠券分类体系已升级为5维度模型 (discountType + couponCategory + grantType + stockType + grabType)。
 *             本枚举保留作为向后兼容，新代码不应使用。
 */
@Deprecated
@Getter
public enum CouponTypeEnum {
    FIXED(1, "满减券"),
    PERCENTAGE(2, "折扣券");

    private final int code;
    private final String desc;

    CouponTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
