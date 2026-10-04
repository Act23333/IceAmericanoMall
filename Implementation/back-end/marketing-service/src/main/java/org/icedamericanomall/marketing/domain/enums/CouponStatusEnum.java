package org.icedamericanomall.marketing.domain.enums;

/**
 * 优惠券模板状态（领域枚举）—— {@code coupon.status} 口径。
 *
 * <p>由原 {@code CouponPO} 静态常量归位到领域层（PO 只留持久化字段）。
 * 兼容历史写法（{@code status == 1} 判断）：取码走 {@link #getCode()}。
 */
public enum CouponStatusEnum {

    /** 草稿（未发布，不可领取）。 */
    DRAFT(0),

    /** 已发布（可领取/可用）。 */
    ACTIVE(1),

    /** 暂停发放。 */
    PAUSED(2),

    /** 已过期。 */
    EXPIRED(3),

    /** 已终止（下线）。 */
    TERMINATED(4);

    private final int code;

    CouponStatusEnum(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** 按数据库码解析；未知码返回 null（调用方按「非 ACTIVE」处理）。 */
    public static CouponStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (CouponStatusEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
