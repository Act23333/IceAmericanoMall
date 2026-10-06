package org.icedamericanomall.pay.adapter.web;

import com.jayway.jsonpath.JsonPath;
import org.icedamericanomall.pay.adapter.response.JsapiPayParamsVO;
import org.icedamericanomall.pay.app.PayManager;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PayController 微信回调应答单元测试 —— 锁死「渠道协议优先于业务信封」。
 *
 * <p>回归背景（P1）：此前成功也返回 {@code Result} 信封（{@code {"code":0,...,"data":"SUCCESS"}}），
 * 不符合微信支付 v3 规范；渠道判定应答不合规并按失败处理，最终停止重推 → 掉单。
 * 现按 v3 规范：成功 HTTP 200 + {@code {"code":"SUCCESS","message":"成功"}}；
 * 失败 HTTP 500 + {@code {"code":"FAIL","message":...}}（触发重推），且 message 不外泄内部细节。
 *
 * <p>入参口径（ADR-033）：控制器只把<b>原始请求头 + 原始报文体</b>透传给 {@link PayManager}，
 * 验签/解密/业务字段提取全部下沉到通道适配层 —— 故此处断言的是
 * {@code handleWechatCallback(headers, rawBody)} 这一整包入参形态，而非任何语义键 Map。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PayController 微信回调应答（v3 规范）")
class PayControllerTest {

    private static final String CALLBACK_PATH = "/api/pay/callback/wechat";

    /** 微信 v3 回调报文体（真实形态：resource 密文，业务字段需解密后才能得到）。 */
    private static final String CALLBACK_BODY = """
            {"id":"EV-1","event_type":"TRANSACTION.SUCCESS",
             "resource":{"algorithm":"AEAD_AES_256_GCM","ciphertext":"BASE64","nonce":"abcd12345678",
                         "associated_data":"transaction"}}""";

    @Mock
    private PayManager payManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PayController(payManager)).build();
    }

    private MvcResult postCallback() throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post(CALLBACK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Wechatpay-Signature", "sig")
                        .header("Wechatpay-Nonce", "nonce")
                        .header("Wechatpay-Timestamp", "1234567890")
                        .header("Wechatpay-Serial", "serial")
                        .content(CALLBACK_BODY))
                .andReturn();
    }

    /** 以 UTF-8 解码响应体（避免 MockHttpServletResponse 默认编码对中文的干扰）。 */
    private String body(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("处理成功 — HTTP 200 + 裸 JSON {\"code\":\"SUCCESS\",\"message\":\"成功\"}（不包 Result 信封）")
    void shouldReturnSuccessBody_whenCallbackHandled() throws Exception {
        MvcResult result = postCallback();
        String body = body(result);

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(PayController.RESPONSE_CODE_SUCCESS, JsonPath.read(body, "$.code"));
        assertEquals(PayController.RESPONSE_MESSAGE_SUCCESS, JsonPath.read(body, "$.message"));
        // 协议报文不得被平台信封包裹
        assertFalse(body.contains("\"result\""), "应答体不应包含 Result 信封字段: " + body);
        assertFalse(body.contains("\"data\""), "应答体不应包含 Result 信封字段: " + body);

        // 控制器只搬运：签名头 + 逐字节原文交给 Manager（不解密、不组装语义键）
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(payManager).handleWechatCallback(headersCaptor.capture(), bodyCaptor.capture());

        Map<String, String> headers = headersCaptor.getValue();
        assertEquals("sig", headers.get("Wechatpay-Signature"));
        assertEquals("nonce", headers.get("Wechatpay-Nonce"));
        assertEquals("1234567890", headers.get("Wechatpay-Timestamp"));
        assertEquals("serial", headers.get("Wechatpay-Serial"));
        assertEquals(CALLBACK_BODY, bodyCaptor.getValue(), "验签要求逐字节原文，不得被控制器改写");
    }

    @Test
    @DisplayName("验签失败 — HTTP 500 + {\"code\":\"FAIL\"}，且 message 不含内部异常文案")
    void shouldReturnFailBodyAnd500_whenSignatureInvalid() throws Exception {
        doThrow(new BizException(ErrorCode.ILLEGAL_REQUEST, "支付回调验证失败"))
                .when(payManager).handleWechatCallback(anyMap(), anyString());

        MvcResult result = postCallback();
        String body = body(result);

        assertEquals(500, result.getResponse().getStatus(), "非 200 才会触发微信重推");
        assertEquals(PayController.RESPONSE_CODE_FAIL, JsonPath.read(body, "$.code"));
        assertEquals(PayController.RESPONSE_MESSAGE_FAIL, JsonPath.read(body, "$.message"));
        assertFalse(body.contains("支付回调验证失败"), "不得外泄内部失败原因: " + body);
        assertFalse(body.contains("ILLEGAL_REQUEST"), "不得外泄错误码: " + body);
    }

    @Test
    @DisplayName("幂等重复通知 — 支付单已推进、幂等命中：仍返回 HTTP 200 + SUCCESS（避免渠道判失败重推）")
    void shouldReturnSuccess_whenDuplicateNotificationIdempotentHit() throws Exception {
        // 重复通知在 PayManager 内走「状态非待支付 → 直接返回」分支，不抛异常
        MvcResult result = postCallback();
        String body = body(result);

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(PayController.RESPONSE_CODE_SUCCESS, JsonPath.read(body, "$.code"));
        assertEquals(PayController.RESPONSE_MESSAGE_SUCCESS, JsonPath.read(body, "$.message"));
    }

    @Test
    @DisplayName("业务处理失败 — HTTP 500 + FAIL（触发微信重推），message 仍为固定文案")
    void shouldReturnFailBodyAnd500_whenBusinessHandlingFails() throws Exception {
        doThrow(new BizException(ErrorCode.ORDER_NOT_FOUND, "支付单不存在: PAY-1"))
                .when(payManager).handleWechatCallback(anyMap(), anyString());

        MvcResult result = postCallback();
        String body = body(result);

        assertEquals(500, result.getResponse().getStatus());
        assertEquals(PayController.RESPONSE_CODE_FAIL, JsonPath.read(body, "$.code"));
        assertEquals(PayController.RESPONSE_MESSAGE_FAIL, JsonPath.read(body, "$.message"));
        assertFalse(body.contains("支付单不存在"), "不得外泄内部失败原因: " + body);
    }

    // ==================== PAY7 小程序拉起（微信 JSAPI） ====================

    @Test
    @DisplayName("PAY7 成功 — HTTP 200 + Result.data 六字段拉起参数集（packageVal 原名透传），委托 Manager")
    void shouldReturnJsapiParams_whenValidJsapiBody() throws Exception {
        JsapiPayParamsVO vo = new JsapiPayParamsVO();
        vo.setAppId("wx-app");
        vo.setTimeStamp("1760000000");
        vo.setNonceStr("nonce-str");
        vo.setPackageVal("prepay_id=wx-1");
        vo.setSignType("RSA");
        vo.setPaySign("pay-sign");
        when(payManager.initiateJsapiPayment(eq("ORD-1"), any(), eq("openid-1"))).thenReturn(vo);

        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/pay/wechat/jsapi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"ORD-1\",\"openid\":\"openid-1\"}"))
                .andReturn();
        String body = body(result);

        assertEquals(200, result.getResponse().getStatus());
        assertEquals("wx-app", JsonPath.read(body, "$.data.appId"));
        assertEquals("1760000000", JsonPath.read(body, "$.data.timeStamp"));
        assertEquals("nonce-str", JsonPath.read(body, "$.data.nonceStr"));
        assertEquals("prepay_id=wx-1", JsonPath.read(body, "$.data.packageVal"),
                "微信协议 package 字段须以 packageVal 原名透传");
        assertEquals("RSA", JsonPath.read(body, "$.data.signType"));
        assertEquals("pay-sign", JsonPath.read(body, "$.data.paySign"));
        // 委托：orderNo/openid 原样透传（standalone MockMvc 无认证上下文，userId 为 null）
        verify(payManager).initiateJsapiPayment("ORD-1", null, "openid-1");
    }

    @Test
    @DisplayName("PAY7 参数校验 — 缺 openid → HTTP 400，Manager 不被调用")
    void shouldReturn400_whenJsapiOpenidMissing() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/pay/wechat/jsapi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"ORD-1\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isBadRequest());

        verify(payManager, never()).initiateJsapiPayment(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("PAY7 参数校验 — 缺 orderNo → HTTP 400，Manager 不被调用")
    void shouldReturn400_whenJsapiOrderNoMissing() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/pay/wechat/jsapi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openid\":\"openid-1\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isBadRequest());

        verify(payManager, never()).initiateJsapiPayment(anyString(), any(), anyString());
    }
}
