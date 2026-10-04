package org.icedamericanomall.integration.payment;

/**
 * 微信支付 v3 回调的强类型解析结果 —— 通道适配层的出参契约（ADR-033）。
 *
 * <p><b>为什么要有这个类型</b>：微信 v3 回调的语义字段（{@code out_trade_no} / {@code trade_state} /
 * {@code transaction_id}）位于 <b>AES-GCM 解密后</b>的 {@code resource} 明文报文里，而不是 HTTP 表单/
 * 查询参数。此前 pay-service 直接把回调参数 {@code Map} 当业务字段源读
 * （{@code params.get("out_trade_no")}）—— 微信路径下该键<b>永不存在</b>，导致支付单必然查不到、
 * 回调必然失败。现在验签与解密归通道适配层（{@link WechatPaymentClient}），应用层只消费本强类型结果，
 * 跨层的字段名字符串契约随之消失。
 *
 * @param outTradeNo   商户订单号（本平台语义下的<b>支付单号</b>，微信 v3 中即 {@code out_trade_no}）
 * @param success      渠道交易是否成功终态（{@code "SUCCESS".equals(tradeState)}）
 * @param channelTradeNo 渠道交易号（微信 {@code transaction_id}，供对账/排障；缺失时可能为 {@code null}）
 * @param rawState     渠道原始交易状态（微信 {@code trade_state}，或 Mock/支付宝口径的 {@code result_code}）；
 *                     非成功终态（如 {@code CLOSED} / {@code PAYERROR}）由调用方记录日志但不推进支付单
 */
public record WechatCallbackResult(String outTradeNo, boolean success, String channelTradeNo, String rawState) {
}
