package org.icedamericanomall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单聚合根 — DDD 充血模型 V5.0
 *
 * <pre>
 * 状态机 (不可逆):
 *   1-PENDING_PAY → 2-PENDING_SHIP → 3-PENDING_RECEIPT → 4-COMPLETED
 *                   ↘ 5-CANCELLED
 *
 * 不变量:
 *   1. totalAmount = payAmount + discountAmount
 *   2. 状态只能按状态机流转，不可回退
 *   3. 只有 PENDING_PAY 状态可取消
 *   4. 已取消/已完成状态不可再变更
 * </pre>
 */
@Getter
@TableName("orders")
public class OrderEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== 状态常量 ====================
    public static final int PENDING_PAY     = 1;
    public static final int PENDING_SHIP    = 2;
    public static final int PENDING_RECEIPT = 3;
    public static final int COMPLETED       = 4;
    public static final int CANCELLED       = 5;
    public static final int PENDING_REVIEW  = 6;

    // ==================== 字段 ====================

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long sellerId;
    private Integer totalAmount;
    private Integer payAmount;
    private Integer discountAmount;
    private Integer status;
    private Integer orderType;
    private Integer paymentType;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime consignTime;
    private LocalDateTime endTime;
    private LocalDateTime closeTime;
    private LocalDateTime commentTime;
    @Version
    private Integer version;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ==================== 领域行为 ====================

    /** 支付成功 → 待发货 */
    public void markPaid() {
        assertStatus(PENDING_PAY, "只有待付款订单可以支付");
        this.status = PENDING_SHIP;
        this.payTime = LocalDateTime.now();
    }

    /** 商家发货 → 待收货 */
    public void markShipped() {
        assertStatus(PENDING_SHIP, "只有待发货订单可以发货");
        this.status = PENDING_RECEIPT;
        this.consignTime = LocalDateTime.now();
    }

    /** 用户确认收货 → 已完成 */
    public void confirmReceipt() {
        assertStatus(PENDING_RECEIPT, "只有待收货订单可以确认收货");
        this.status = COMPLETED;
        this.endTime = LocalDateTime.now();
    }

    /** 取消订单（仅待付款状态） */
    public void cancel() {
        assertStatus(PENDING_PAY, "只有待付款订单可以取消");
        this.status = CANCELLED;
        this.closeTime = LocalDateTime.now();
    }

    /** 超时取消 */
    public void cancelTimeout() {
        assertStatus(PENDING_PAY, "只有待付款订单可以超时取消");
        this.status = CANCELLED;
        this.closeTime = LocalDateTime.now();
    }

    /** 标记为待评价 */
    public void markPendingReview() {
        assertStatus(COMPLETED, "只有已完成订单可以评价");
        this.status = PENDING_REVIEW;
    }

    // ==================== 查询方法 ====================

    public boolean isPendingPay()     { return this.status == PENDING_PAY; }
    public boolean isPendingShip()    { return this.status == PENDING_SHIP; }
    public boolean isPendingReceipt() { return this.status == PENDING_RECEIPT; }
    public boolean isCompleted()      { return this.status == COMPLETED; }
    public boolean isCancelled()      { return this.status == CANCELLED; }
    public boolean isTerminal()       { return this.status == COMPLETED || this.status == CANCELLED; }

    // ==================== 内部断言 ====================

    private void assertStatus(int expected, String msg) {
        if (this.status != expected) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, msg + "，当前状态: " + this.status);
        }
    }

    // ==================== 持久层 setter (仅 Repository/MyBatis 使用) ====================

    public void setId(Long id) { this.id = id; }
    public void setOrderNo(String v) { this.orderNo = v; }
    public void setUserId(Long v) { this.userId = v; }
    public void setSellerId(Long v) { this.sellerId = v; }
    public void setTotalAmount(Integer v) { this.totalAmount = v; }
    public void setPayAmount(Integer v) { this.payAmount = v; }
    public void setDiscountAmount(Integer v) { this.discountAmount = v; }
    public void setStatus(Integer v) { this.status = v; }
    public void setOrderType(Integer v) { this.orderType = v; }
    public void setPaymentType(Integer v) { this.paymentType = v; }
    public void setReceiverName(String v) { this.receiverName = v; }
    public void setReceiverPhone(String v) { this.receiverPhone = v; }
    public void setReceiverAddress(String v) { this.receiverAddress = v; }
    public void setCreateTime(LocalDateTime v) { this.createTime = v; }
    public void setPayTime(LocalDateTime v) { this.payTime = v; }
    public void setConsignTime(LocalDateTime v) { this.consignTime = v; }
    public void setEndTime(LocalDateTime v) { this.endTime = v; }
    public void setCloseTime(LocalDateTime v) { this.closeTime = v; }
    public void setCommentTime(LocalDateTime v) { this.commentTime = v; }
    public void setVersion(Integer v) { this.version = v; }
    public void setUpdateTime(LocalDateTime v) { this.updateTime = v; }
}
