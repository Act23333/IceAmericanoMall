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

    /**
     * 买家留言最大长度（V5.7）—— 编译期常量，供适配层 {@code @Size(max = ...)} 注解
     * 与单元测试共用，避免魔法值双写漂移（注解只接受编译期常量，故为
     * {@code public static final int}）。
     */
    public static final int REMARK_MAX_LENGTH = 200;

    /**
     * 展示币种代码最大长度（V5.17 多币种）—— ISO 4217 alpha-3 定长 3 字符，供适配层
     * {@code @Size(max = ...)} 注解与单元测试共用，避免魔法值双写漂移（同
     * {@link #REMARK_MAX_LENGTH} 体例）。数据库列 {@code orders.display_currency VARCHAR(3)}。
     */
    public static final int DISPLAY_CURRENCY_MAX_LENGTH = 3;

    private Long id;
    private String orderNo;
    private Long userId;
    private Long sellerId;
    private Integer totalAmount;
    private Integer payAmount;
    private Integer discountAmount;

    /** 运费（分）—— 下单时按运费模板计算并<b>快照</b>（V5.6，ADR-037）。 */
    private Integer postFee;

    /**
     * 买家留言快照（V5.7）—— 下单时随单落库（T1/T6 请求增量，≤ {@link #REMARK_MAX_LENGTH}，
     * 可选），Phase 1 不提供修改端点；秒杀等无留言渠道的订单类型为 {@code null}。
     */
    private String remark;

    /**
     * 展示币种快照（V5.17 多币种 Phase 1，F16，ISO 4217，可空）—— 下单时点展示参考快照，
     * {@code null} = CNY 订单（未传/主币种直通）或下单时快照降级（fail-open，PRD §17.7.2）。
     * <b>展示参考、非结算币种</b>（PRD 裁决 D1-B/D3-B）：不参与金额不变式 V2
     * （{@code payAmount = totalAmount − discountAmount + postFee}，四金额字段恒 CNY 分），
     * 支付/退款/钱包/对账等资金链路永不读取本字段。
     */
    private String displayCurrency;

    /**
     * 下单时点定点整数汇率快照（V5.17 多币种 Phase 1，可空）—— 每 1 元人民币兑换目标币
     * 主单位数 × 10⁶（如 USD 0.140300 → 140300）。<b>展示参考、非结算参数，不参与金额不变式
     * V2</b>；与 {@link #displayCurrency} 同生共死：仅快照成功后两者非空，
     * 未传/CNY/降级（哨兵 -1/未启用 1103/异常）两者皆空。
     */
    private Long displayRate;

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
