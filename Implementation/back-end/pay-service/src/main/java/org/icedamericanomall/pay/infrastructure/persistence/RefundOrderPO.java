package org.icedamericanomall.pay.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 退款单实体 —— 持久化模型（{@code refund_order} 表，F1 买家退款资金流）。
 *
 * <pre>
 * 状态机 (统一见 {@link org.icedamericanomall.pay.domain.enums.RefundStatusEnum}):
 *   0-PROCESSING → 1-SUCCESS
 *                ↘ 2-FAILED ──(管理端 AD43 重试)──→ 0-PROCESSING
 *
 * 不变量:
 *   1. biz_ref 唯一（uk_biz_ref）—— 统一幂等业务键：售后=AS:{afterSaleId}，拼团=GB:{orderNo}（ADR-049）
 *   2. after_sale_id 唯一且可空（uk_after_sale）—— 售后场景一售后单一次退款；无售后场景为 null
 *   3. refund_no 唯一（uk_refund_no）—— 对外定位键与管理端重试定位键
 *   4. 成功迁移只在「处理中」态发生（条件更新），保证回调重复幂等
 *   5. 售后单状态仅在退款成功后回写（PROCESSING / FAILED 时停留 APPROVED）；无售后单退款跳过回写
 * </pre>
 */
@Data
@TableName("refund_order")
public class RefundOrderPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 退款单号（业务唯一键，UUID）。 */
    private String refundNo;

    /** 关联订单号（orders.order_no）。 */
    private String orderNo;

    /** 关联支付单号（pay_order.pay_order_no）。 */
    private String payOrderNo;

    /** 关联售后单 ID（唯一约束，一售后单一次退款）；无售后单退款（I55 拼团）为 NULL（ADR-049）。 */
    private Long afterSaleId;

    /** 统一幂等业务键（uk_biz_ref）：售后=AS:{afterSaleId}，拼团=GB:{orderNo}（ADR-049）。 */
    private String bizRef;

    /** 买家用户 ID。 */
    private Long userId;

    /** 退款渠道（与原支付渠道一致，原路退回）：WECHAT / ALIPAY / BALANCE。 */
    private String channel;

    /** 退款金额（分）。 */
    private Integer refundAmount;

    /** 状态：0-处理中 / 1-成功 / 2-失败。 */
    private Integer status;

    /** 渠道退款单号（微信 refund_id 等，回调回填）。 */
    private String channelRefundNo;

    /** 失败原因（渠道返回或系统错误摘要，不含敏感细节）。 */
    private String failReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
