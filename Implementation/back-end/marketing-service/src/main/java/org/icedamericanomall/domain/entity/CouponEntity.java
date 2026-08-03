package org.icedamericanomall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 优惠券聚合根 — DDD 充血模型 V5.0
 *
 * <pre>
 * 不变量:
 *   1. issuedQty <= totalQty（限量券）
 *   2. status=ACTIVE 才能领取/使用
 *   3. 当前时间在 startTime~endTime 之间才能使用
 *   4. grabType=NEED_GRAB 走 Redis Lua 原子通道
 * </pre>
 */
@Getter
@TableName("coupon")
public class CouponEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== 状态常量 ====================
    public static final int DRAFT      = 0;
    public static final int ACTIVE     = 1;
    public static final int PAUSED     = 2;
    public static final int EXPIRED    = 3;
    public static final int TERMINATED = 4;

    // ==================== 折扣类型 ====================
    public static final int DISCOUNT_FIXED       = 1;
    public static final int DISCOUNT_PERCENTAGE  = 2;
    public static final int DISCOUNT_CASH_COUPON = 3;

    // ==================== 字段 ====================

    @TableId(type = IdType.AUTO)
    private Long id;
    private String couponId;
    private String name;
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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ==================== 领域行为 ====================

    /** 领取一张优惠券（限量券校验库存） */
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

    /** 回滚一张已领取的优惠券 */
    public void rollbackClaim() {
        if (this.totalQty != null && this.totalQty > 0 && this.issuedQty > 0) {
            this.issuedQty--;
        }
    }

    /**
     * 计算折扣金额。
     *
     * @param orderAmount 订单金额（分）
     * @return 折扣金额（分），不满足门槛返回0
     */
    public int calculateDiscount(int orderAmount) {
        assertActive("只有已发布的券可以使用");
        assertNotExpired("优惠券已过期");

        if (this.minAmount != null && this.minAmount > 0 && orderAmount < this.minAmount) {
            return 0; // 不满足最低消费
        }

        int discount = switch (this.discountType != null ? this.discountType : 1) {
            case DISCOUNT_PERCENTAGE -> {
                int pct = this.value != null ? this.value : 0;
                yield orderAmount * (100 - pct) / 100;
            }
            case DISCOUNT_CASH_COUPON -> this.value != null ? Math.min(this.value, orderAmount) : 0;
            default -> this.value != null ? Math.min(this.value, orderAmount) : 0; // FIXED
        };
        return Math.min(discount, orderAmount);
    }

    /** 发布优惠券 */
    public void publish() {
        if (this.status != DRAFT) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "只有草稿状态的券可以发布");
        this.status = ACTIVE;
    }

    /** 暂停发放 */
    public void pause() {
        assertActive("只有已发布的券可以暂停");
        this.status = PAUSED;
    }

    /** 终止 */
    public void terminate() {
        if (this.status == TERMINATED) return;
        this.status = TERMINATED;
    }

    // ==================== 查询方法 ====================

    public boolean isActive()          { return this.status == ACTIVE; }
    public boolean isExpired()         { return this.endTime != null && LocalDateTime.now().isAfter(this.endTime); }
    public boolean isGrabType()        { return this.grabType != null && this.grabType == 2; }
    public boolean hasStock()          { return this.totalQty == null || this.totalQty <= 0 || this.issuedQty < this.totalQty; }

    // ==================== 内部校验 ====================

    private void assertActive(String msg) {
        if (this.status != ACTIVE) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, msg);
    }

    private void assertNotExpired(String msg) {
        if (this.endTime != null && LocalDateTime.now().isAfter(this.endTime))
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, msg);
    }

    // ==================== 持久层 setter (仅 MyBatis 使用) ====================

    public void setId(Long v) { this.id = v; }
    public void setCouponId(String v) { this.couponId = v; }
    public void setName(String v) { this.name = v; }
    public void setType(Integer v) { this.type = v; }
    public void setValue(Integer v) { this.value = v; }
    public void setMinAmount(Integer v) { this.minAmount = v; }
    public void setTotalQty(Integer v) { this.totalQty = v; }
    public void setIssuedQty(Integer v) { this.issuedQty = v; }
    public void setStartTime(LocalDateTime v) { this.startTime = v; }
    public void setEndTime(LocalDateTime v) { this.endTime = v; }
    public void setStatus(Integer v) { this.status = v; }
    public void setSellerId(Long v) { this.sellerId = v; }
    public void setDiscountType(Integer v) { this.discountType = v; }
    public void setCouponCategory(Integer v) { this.couponCategory = v; }
    public void setGrantType(Integer v) { this.grantType = v; }
    public void setStockType(Integer v) { this.stockType = v; }
    public void setGrabType(Integer v) { this.grabType = v; }
    public void setPriceInCents(Integer v) { this.priceInCents = v; }
    public void setCreateTime(LocalDateTime v) { this.createTime = v; }
    public void setUpdateTime(LocalDateTime v) { this.updateTime = v; }
}
