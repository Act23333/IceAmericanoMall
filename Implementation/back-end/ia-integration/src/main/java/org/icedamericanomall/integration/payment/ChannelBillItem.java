package org.icedamericanomall.integration.payment;

/**
 * 渠道账单条目 —— 渠道对账（F4）的账单行口径，通道适配层的出参契约。
 *
 * <p><b>为什么字段名是 {@code outTradeNo}</b>：渠道账单里的「商户订单号」列即本平台发起支付时
 * 传下去的 {@code out_trade_no}，本平台语义下它就是 {@code pay_order.pay_order_no}
 * （见 {@code PayOrderServiceImpl#createPending}）。对账侧按 {@code pay_order_no} 与本结构比对。
 *
 * @param outTradeNo    渠道账单中的商户订单号（本平台支付单号 {@code pay_order_no}）
 * @param channelTxnId  渠道交易号（微信 {@code transaction_id}；渠道账单缺失时为 {@code null}）
 * @param amount        金额（<b>分</b>；渠道账单以「元」计价，适配层内部完成元→分转换，不外泄小数）
 */
public record ChannelBillItem(String outTradeNo, String channelTxnId, Integer amount) {
}
