package org.icedamericanomall.trade.domain.model.order;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单聚合（纯 POJO，零框架依赖）—— {@code orders} 表。
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
 * 说明: 状态迁移由 OrderRepository 以「条件更新」实现（WHERE 带期望状态，保证并发安全），
 * 本类不再自带状态机行为方法，避免领域模型与状态码双轨。
 * </pre>
 */
@Data
public class Order {

    private Long id;
    private String orderNo;
    private Long userId;
    private Long sellerId;
    private Integer totalAmount;
    private Integer payAmount;
    private Integer discountAmount;

    /** 运费（分）—— 下单时按运费模板计算并<b>快照</b>（V5.6，ADR-037）。 */
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

    /** 乐观锁版本号（持久化层列，条件更新用）。 */
    private Integer version;
    private LocalDateTime updateTime;
}
