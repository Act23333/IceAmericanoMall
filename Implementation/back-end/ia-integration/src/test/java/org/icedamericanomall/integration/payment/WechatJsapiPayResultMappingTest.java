package org.icedamericanomall.integration.payment;

import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 微信 JSAPI 下单（PAY7）—— SDK 拉起参数集映射与配置字段测试。
 *
 * <p>真实实现的下单链路依赖微信证书配置（{@code @PostConstruct} 初始化 + 真实 HTTP），
 * 按 {@link WechatRefundQueryMappingTest} 先例，此处聚焦可纯静态验证的部分：
 * {@code PrepayWithRequestPaymentResponse} → {@link JsapiPayResult} 六字段一一对应
 * （含 {@code packageVal} 原名保留），以及 {@code appId} 配置字段可绑定
 * （appid/openid 同源约束落地的载体）。
 */
@DisplayName("微信 JSAPI 下单 — SDK 参数集映射与 appId 配置")
class WechatJsapiPayResultMappingTest {

    @Test
    @DisplayName("SDK 拉起参数集 → JsapiPayResult 六字段一一对应（packageVal 原名保留）")
    void shouldMapAllSixFields_whenResponseMapped() {
        PrepayWithRequestPaymentResponse response = new PrepayWithRequestPaymentResponse();
        response.setAppId("wx-app");
        response.setTimeStamp("1760000000");
        response.setNonceStr("nonce-str");
        response.setPackageVal("prepay_id=wx20261006");
        response.setSignType("RSA");
        response.setPaySign("pay-sign");

        JsapiPayResult result = WechatPaymentClient.toJsapiPayResult(response);

        assertEquals("wx-app", result.appId());
        assertEquals("1760000000", result.timeStamp());
        assertEquals("nonce-str", result.nonceStr());
        assertEquals("prepay_id=wx20261006", result.packageVal(), "SDK package 字段须以 packageVal 原名透传");
        assertEquals("RSA", result.signType());
        assertEquals("pay-sign", result.paySign());
    }

    @Test
    @DisplayName("WechatPayProperties.appId 可按 wechat.pay.app-id 绑定（openid 同源约束的配置载体）")
    void shouldBindAppId_whenPropertiesConfigured() {
        WechatPayProperties props = new WechatPayProperties();
        props.setAppId("wx-mini-appid");

        assertNotNull(props.getAppId());
        assertEquals("wx-mini-appid", props.getAppId());
    }
}
