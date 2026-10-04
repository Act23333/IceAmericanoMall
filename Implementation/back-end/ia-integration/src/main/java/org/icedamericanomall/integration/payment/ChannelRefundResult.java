package org.icedamericanomall.integration.payment;

/**
 * 渠道退款受理结果 —— 退款发起的通道适配层出参契约（F1 买家退款资金流）。
 *
 * <p><b>两种终局</b>：
 * <ul>
 *   <li>{@code settled = true} —— 渠道已同步确认退款成功（余额渠道本地入账；支付宝 Mock 同步成功），
 *       调用方（pay-service）可直接把退款单置成功；</li>
 *   <li>{@code settled = false} —— 渠道已受理但尚在处理（微信 v3 退款多数场景返回 {@code PROCESSING}），
 *       调用方保持「处理中」，由渠道退款回调（PAY6）确认终态。</li>
 * </ul>
 *
 * <p><b>失败不走本结构</b>：渠道明确拒绝（SDK 抛异常 / 返回错误码）一律抛
 * {@code BizException}，由调用方置退款单「失败」并保留管理端重试入口——退款发起失败
 * 不允许静默丢失（ADR-004 / ADR-011 fail-closed 口径）。
 *
 * @param channelRefundNo 渠道退款单号（微信 {@code refund_id}；Mock/支付宝可能为 {@code null} 或模拟号）
 * @param settled         渠道是否已同步确认成功
 */
public record ChannelRefundResult(String channelRefundNo, boolean settled) {

    /** 渠道同步确认成功。 */
    public static ChannelRefundResult settled(String channelRefundNo) {
        return new ChannelRefundResult(channelRefundNo, true);
    }

    /** 渠道已受理、处理中（等回调确认）。 */
    public static ChannelRefundResult processing(String channelRefundNo) {
        return new ChannelRefundResult(channelRefundNo, false);
    }
}
