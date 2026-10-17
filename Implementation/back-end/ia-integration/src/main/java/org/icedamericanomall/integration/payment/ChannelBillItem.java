package org.icedamericanomall.integration.payment;

/**
 * 渠道账单条目 —— 渠道对账（F4）的账单行口径，通道适配层的出参契约。
 *
 * <p><b>为什么字段名是 {@code outTradeNo}</b>：渠道账单里的「商户订单号」列即本平台发起支付时
 * 传下去的 {@code out_trade_no}，<b>本平台语义下它就是 {@code pay_order.channel_trade_no}
 * （对外渠道号）</b> —— <b>不是</b> {@code pay_order.pay_order_no}，也<b>不是</b>
 * {@code orders.order_no}。对账侧据 {@link #outTradeNo()} 与本地
 * {@code pay_order.channel_trade_no} 比对（见 {@code ReconciliationComparer}）。
 *
 * <p><b>历史缺陷（已修）</b>：本注释此前自称 {@code outTradeNo} 就是 {@code pay_order_no}，
 * 于是对账用「支付单号」与账单比对、回调用「支付单号」查单；而发起侧实际把
 * {@code orders.order_no} 当 {@code out_trade_no} 传给了渠道 —— 两个口径从未对齐，
 * 真实渠道回调/账单必然查不到本地支付单。现在对外号是独立列 {@code channel_trade_no}，
 * 该字段的语义<b>只有</b>「渠道号」一种读法。
 *
 * @param outTradeNo    渠道账单中的商户订单号（本平台对外渠道号 {@code pay_order.channel_trade_no}）
 * @param channelTxnId  渠道交易号（微信 {@code transaction_id}；渠道账单缺失时为 {@code null}）
 * @param amount        金额（<b>分</b>；渠道账单以「元」计价，适配层内部完成元→分转换，不外泄小数）
 */
public record ChannelBillItem(String outTradeNo, String channelTxnId, Integer amount) {
}
