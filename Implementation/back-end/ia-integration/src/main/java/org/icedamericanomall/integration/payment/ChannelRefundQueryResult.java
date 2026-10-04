package org.icedamericanomall.integration.payment;

/**
 * 渠道退款查询结果 —— 悬挂退款补偿（ADR-035 风险收敛）的通道适配层出参契约。
 *
 * <p>与 {@link ChannelRefundResult}（退款<b>发起</b>受理结果）分开：查询是「事后对渠道终态的
 * 主动确认」，语义与受理结果不同域，合进一个 record 会让两种口径互相含混（同 ADR-033 拆分回调结果的理据）。
 *
 * @param refundId 渠道退款单号（微信 {@code refund_id}，回填 {@code refund_order.channel_refund_no}）
 * @param state    归一化退款状态（渠道原始状态串不越过适配层边界）
 */
public record ChannelRefundQueryResult(String refundId, ChannelRefundState state) {
}
