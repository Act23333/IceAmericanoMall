package org.icedamericanomall.marketing.domain.enums;

/**
 * 用户持券状态（领域枚举）—— {@code user_coupon.status} 口径。
 *
 * <p>由原服务实现内的私有常量（STATUS_UNUSED/STATUS_USED）归位到领域层。
 */
public enum UserCouponStatusEnum {

    /** 未使用。 */
    UNUSED(1),

    /** 已使用（绑定订单号）。 */
    USED(2),

    /** 已过期。 */
    EXPIRED(3);

    private final int code;

    UserCouponStatusEnum(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** 按数据库码解析；未知码返回 null。 */
    public static UserCouponStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (UserCouponStatusEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
