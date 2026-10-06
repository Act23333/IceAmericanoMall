package org.icedamericanomall.integration.payment;

/**
 * 微信 JSAPI 小程序拉起支付参数集（PAY7）—— SDK
 * {@code PrepayWithRequestPaymentResponse} 六字段的一一对应映射，
 * 前端原样直传 {@code wx.requestPayment} 拉起收银台。
 *
 * <p>字段命名与 SDK 响应体严格对齐：SDK 的 {@code package} 字段在 Java 中取原名
 * {@code packageVal}（{@code package} 是语言关键字），调用方与前端契约同样使用
 * {@code packageVal}，不做改名转换。
 *
 * @param appId      商户下单 appid（必须与 openid 同源：小程序场景即小程序 appid）
 * @param timeStamp  签名时间戳（秒级字符串）
 * @param nonceStr   签名随机串
 * @param packageVal 统一下单返回的 {@code prepay_id=...}（SDK 原名 packageVal）
 * @param signType   签名类型（RSA）
 * @param paySign    商户签名
 */
public record JsapiPayResult(String appId, String timeStamp, String nonceStr,
                             String packageVal, String signType, String paySign) {
}
