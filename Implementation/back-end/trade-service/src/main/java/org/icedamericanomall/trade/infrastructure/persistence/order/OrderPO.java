package org.icedamericanomall.trade.infrastructure.persistence.order;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单实体 —— 持久化模型（{@code orders} 表）。
 *
 * <pre>
 * 状态机 (不可逆，状态码统一见 {@link org.icedamericanomall.trade.domain.enums.OrderStatusEnum}):
 *   1-PENDING_PAYMENT → 2-PENDING_SHIPMENT → 3-PENDING_RECEIPT → 4-COMPLETED
 *                     ↘ 5-CANCELLED
 *
 * 不变量:
 *   1. totalAmount = Σ order_item.sub_total（不含运费）
 *   2. payAmount = totalAmount − discountAmount + postFee   （V5.6 运费模型，ADR-037）
 *   3. 状态只能按状态机流转，不可回退
 *   4. 只有 PENDING_PAYMENT 状态可取消
 *
 * 说明: 状态迁移由 OrderService 以「条件更新」实现（WHERE 带期望状态，保证并发安全），
 * 本类不再自带状态机行为方法，避免持久化模型与领域状态码双轨。
 * </pre>
 */
@Getter
@TableName("orders")
public class OrderPO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== 字段 ====================

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long sellerId;
    private Integer totalAmount;
    private Integer payAmount;
    private Integer discountAmount;

    /** 运费（分）—— 下单时按运费模板计算并<b>快照</b>（V5.6，ADR-037）：模板后续改价/停用不影响已建订单。 */
    private Integer postFee;

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

    // ==================== 持久层 setter (仅 MyBatis 使用) ====================

    public void setId(Long id) { this.id = id; }
    public void setOrderNo(String v) { this.orderNo = v; }
    public void setUserId(Long v) { this.userId = v; }
    public void setSellerId(Long v) { this.sellerId = v; }
    public void setTotalAmount(Integer v) { this.totalAmount = v; }
    public void setPayAmount(Integer v) { this.payAmount = v; }
    public void setDiscountAmount(Integer v) { this.discountAmount = v; }
    public void setPostFee(Integer v) { this.postFee = v; }
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
