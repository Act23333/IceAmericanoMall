package org.icedamericanomall.integration.payment;

/**
 * 微信支付 v3 <b>退款</b>回调的强类型解析结果 —— 通道适配层的出参契约（沿用 ADR-033 口径）。
 *
 * <p><b>为什么与 {@link WechatCallbackResult} 分开</b>：退款通知的报文业务字段是
 * {@code out_refund_no} / {@code refund_id} / {@code refund_status}，与支付通知的
 * {@code out_trade_no} / {@code transaction_id} / {@code trade_state} 完全不同域；
 * 合进一个 record 会让「字段在哪个场景有意义」变得含混（正是 ADR-033 要消除的跨层魔法串问题）。
 * 两者共用同一套「验签 + AES-GCM 解密」基建（{@link WechatCallbackDecoder}）与同一应答协议（ADR-029）。
 *
 * @param outRefundNo     商户退款单号（本平台语义下的 {@code refund_order.refund_no}）
 * @param success         渠道退款是否成功终态（{@code "SUCCESS".equals(refund_status)}）
 * @param refundId        渠道退款单号（微信 {@code refund_id}，回填 {@code refund_order.channel_refund_no}）
 * @param rawRefundStatus 渠道原始退款状态（微信 {@code refund_status}：SUCCESS / CLOSED / ABNORMAL）
 */
public record WechatRefundCallbackResult(String outRefundNo, boolean success, String refundId,
                                         String rawRefundStatus) {
}
