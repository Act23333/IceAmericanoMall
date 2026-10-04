package org.icedamericanomall.marketing.domain.model.coupon;

import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.marketing.domain.enums.UserCouponStatusEnum;

import java.time.LocalDateTime;

/**
 * 用户持券聚合（纯 POJO）—— {@code user_coupon} 表，一张用户持有的券实例。
 *
 * <p>状态机：UNUSED（1）→ USED（2，绑定订单号）/ EXPIRED（3）；
 * 订单取消/超时时由 USED 回滚 UNUSED（见 {@link #restore()}）。
 */
@Data
@Builder
public class UserCoupon {

    private Long id;
    private Long userId;
    private Long couponId;
    private Integer status;
    private String usedOrderNo;
    private LocalDateTime useTime;
    private LocalDateTime createTime;

    /** 发放一张可用券（领取/发券落库前构造，id 由仓储回填）。 */
    public static UserCoupon issue(Long userId, Long couponId) {
        return UserCoupon.builder()
                .userId(userId)
                .couponId(couponId)
                .status(UserCouponStatusEnum.UNUSED.getCode())
                .build();
    }

    /** 是否未使用。 */
    public boolean isUnused() {
        return UserCouponStatusEnum.UNUSED.getCode() == this.status;
    }

    /** 是否已使用。 */
    public boolean isUsed() {
        return UserCouponStatusEnum.USED.getCode() == this.status;
    }

    /** 是否归属指定用户。 */
    public boolean isOwnedBy(Long userId) {
        return this.userId != null && this.userId.equals(userId);
    }

    /** 核销：绑定订单号并记使用时间。 */
    public void markUsed(String orderNo) {
        this.status = UserCouponStatusEnum.USED.getCode();
        this.usedOrderNo = orderNo;
        this.useTime = LocalDateTime.now();
    }

    /** 回滚为可用（清空订单绑定与使用时间；订单取消/超时补偿）。 */
    public void restore() {
        this.status = UserCouponStatusEnum.UNUSED.getCode();
        this.usedOrderNo = null;
        this.useTime = null;
    }
}
