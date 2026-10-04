package org.icedamericanomall.pay.adapter.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 退款单视图对象（F1）—— 管理端重试 AD43 返回；内部端点 I40 返回其字段子集
 * （{@code RefundResultCO { refundNo, status }}）。
 *
 * <p>不暴露技术字段（{@code id} / 审计字段 {@code updateTime}）。
 * 契约见 {@code 05-API-Specification.md} §4.7.3。
 */
@Data
public class RefundVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 退款单号（对外定位键）。 */
    private String refundNo;

    /** 关联售后单 ID。 */
    private Long afterSaleId;

    /** 关联订单号。 */
    private String orderNo;

    /** 退款渠道：WECHAT / ALIPAY / BALANCE（原路退回）。 */
    private String channel;

    /** 退款金额（分）。 */
    private Integer refundAmount;

    /** 状态：0-处理中 / 1-成功 / 2-失败。 */
    private Integer status;

    /** 渠道退款单号（微信 refund_id 等，回调回填）。 */
    private String channelRefundNo;

    /** 失败原因（可空）。 */
    private String failReason;

    /** 创建时间。 */
    private LocalDateTime createTime;
}
