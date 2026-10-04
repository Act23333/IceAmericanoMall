package org.icedamericanomall.marketing.domain.model.coupon;

import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.domain.enums.CouponStatusEnum;
import org.icedamericanomall.marketing.domain.service.CouponPricing;

import java.time.LocalDateTime;

/**
 * 优惠券模板聚合（纯 POJO，零框架依赖）—— 由原 {@code CouponPO} 的领域行为归位而来。
 *
 * <pre>
 * 不变量:
 *   1. issuedQty &lt;= totalQty（限量券；原子条件 UPDATE 兜底，本类做内存态自增）
 *   2. status=ACTIVE 才能领取/使用
 *   3. 当前时间在 startTime~endTime 之间才能领取/使用
 *   4. grabType=NEED_GRAB 走 Redis Lua 原子通道（端口见 domain.port.CouponGrabStore）
 * </pre>
 *
 * <p>折扣口径唯一来源：{@link CouponPricing}（本类 {@link #calculateDiscount(int)} 委托）。
 */
@Data
@Builder
public class Coupon {

    private Long id;
    private String couponId;
    private String name;

    /** DEPRECATED（V4.0）历史字段：折扣类型优先读 {@code discountType}。 */
    private Integer type;

    private Integer value;
    private Integer minAmount;
    private Integer totalQty;
    private Integer issuedQty;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private Long sellerId;
    private Integer discountType;
    private Integer couponCategory;
    private Integer grantType;
    private Integer stockType;
    private Integer grabType;
    private Integer priceInCents;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // ==================== 状态迁移 ====================

    /** 领取一张优惠券（限量券内存态自增；DB 原子条件扣减在仓储层）。 */
    public void claim() {
        assertActive("只有已发布的券可以领取");
        assertNotExpired("优惠券已过期");
        if (this.totalQty != null && this.totalQty > 0) {
            if (this.issuedQty >= this.totalQty) {
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券已被领完");
            }
            this.issuedQty++;
        }
    }

    /** 回滚一张已领取的优惠券。 */
    public void rollbackClaim() {
        if (this.totalQty != null && this.totalQty > 0 && this.issuedQty > 0) {
            this.issuedQty--;
        }
    }

    /** 发布优惠券（草稿 → 已发布）。 */
    public void publish() {
        if (CouponStatusEnum.DRAFT.getCode() != this.status) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "只有草稿状态的券可以发布");
        }
        this.status = CouponStatusEnum.ACTIVE.getCode();
    }

    /** 暂停发放（已发布 → 暂停）。 */
    public void pause() {
        assertActive("只有已发布的券可以暂停");
        this.status = CouponStatusEnum.PAUSED.getCode();
    }

    /** 终止（幂等：已终止重复调用不写库）。 */
    public void terminate() {
        if (CouponStatusEnum.TERMINATED.getCode() == this.status) {
            return;
        }
        this.status = CouponStatusEnum.TERMINATED.getCode();
    }

    // ==================== 业务计算 ====================

    /**
     * 计算抵扣金额（委托纯领域服务 {@link CouponPricing}，口径唯一）。
     *
     * @param orderAmount 订单金额（分）
     * @return 折扣金额（分），不满足门槛返回 0
     */
    public int calculateDiscount(int orderAmount) {
        assertActive("只有已发布的券可以使用");
        assertNotExpired("优惠券已过期");
        int discountType = CouponPricing.resolveDiscountType(this.discountType, this.type);
        return CouponPricing.calculate(discountType, this.value, this.minAmount, orderAmount);
    }

    // ==================== 查询 ====================

    public boolean isActive() {
        return CouponStatusEnum.ACTIVE.getCode() == this.status;
    }

    public boolean isExpired() {
        return this.endTime != null && LocalDateTime.now().isAfter(this.endTime);
    }

    /** 是否秒杀级领取券（grabType=NEED_GRAB）。 */
    public boolean isGrabType() {
        return this.grabType != null && this.grabType == 2;
    }

    /** 是否仍有库存（不限量券恒有库存）。 */
    public boolean hasStock() {
        return this.totalQty == null || this.totalQty <= 0 || this.issuedQty < this.totalQty;
    }

    // ==================== 内部校验 ====================

    private void assertActive(String msg) {
        if (CouponStatusEnum.ACTIVE.getCode() != this.status) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, msg);
        }
    }

    private void assertNotExpired(String msg) {
        if (this.endTime != null && LocalDateTime.now().isAfter(this.endTime)) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, msg);
        }
    }
}
