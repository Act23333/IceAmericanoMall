package org.icedamericanomall.pay.adapter.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 微信 JSAPI 小程序拉起支付参数集（PAY7 应答）—— 与渠道适配层
 * {@code JsapiPayResult} 六字段一一对应，前端<b>原样直传</b> {@code wx.requestPayment}。
 *
 * <p>{@code packageVal} 沿用微信协议字段 {@code package} 的 SDK 原名（{@code package}
 * 是 Java 关键字），前端组装拉起参数时须将其映射回 {@code package} 键。
 */
@Data
public class JsapiPayParamsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商户下单 appid（必须与 openid 同源）。 */
    private String appId;

    /** 签名时间戳（秒级字符串）。 */
    private String timeStamp;

    /** 签名随机串。 */
    private String nonceStr;

    /** 统一下单返回的 {@code prepay_id=...}（微信协议 package 字段值）。 */
    private String packageVal;

    /** 签名类型（RSA）。 */
    private String signType;

    /** 商户签名。 */
    private String paySign;
}
