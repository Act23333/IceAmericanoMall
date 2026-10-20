package org.icedamericanomall.pay.adapter.web;

import com.jayway.jsonpath.JsonPath;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.pay.app.ChannelCallbackManager;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * RefundController 微信退款回调（PAY6）应答单元测试 —— 锁死「渠道协议优先于业务信封」与
 * 「控制器只搬运原始头 + 原始报文」（ADR-029 / ADR-033）。
 *
 * <p>对应 F1 BDD Scenario 2 的应答断言：
 * 成功 → 200 + {@code {"code":"SUCCESS","message":"成功"}}；失败 → 500 + FAIL（触发渠道重推）；
 * 重复通知命中幂等分支仍 200 + SUCCESS。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RefundController 微信退款回调（v3 规范应答）")
class RefundControllerTest {

    private static final String CALLBACK_PATH = "/api/pay/callback/wechat-refund";

    /** 微信 v3 退款回调原文（真实形态：resource 密文，退款字段需解密后才可得）。 */
    private static final String CALLBACK_BODY = """
            {"id":"EV-RFD-1","event_type":"REFUND.SUCCESS",
             "resource":{"algorithm":"AEAD_AES_256_GCM","ciphertext":"BASE64","nonce":"abcd12345678",
                         "associated_data":"refund"}}""";

    @Mock
    private ChannelCallbackManager callbackManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RefundController(callbackManager)).build();
    }

    private MvcResult postCallback() throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(CALLBACK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Wechatpay-Signature", "sig")
                        .header("Wechatpay-Nonce", "nonce")
                        .header("Wechatpay-Timestamp", "1234567890")
                        .header("Wechatpay-Serial", "serial")
                        .content(CALLBACK_BODY))
                .andReturn();
    }

    private String body(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("退款成功通知 — 200 + 裸 JSON SUCCESS（不包 Result 信封），且原始头/报文被透传")
    void shouldReturnSuccess_whenRefundCallbackHandled() throws Exception {
        MvcResult result = postCallback();
        String body = body(result);

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(WechatCallbackSupport.CODE_SUCCESS, JsonPath.read(body, "$.code"));
        assertEquals(WechatCallbackSupport.MESSAGE_SUCCESS, JsonPath.read(body, "$.message"));
        assertFalse(body.contains("\"data\""), "应答体不应包含 Result 信封字段: " + body);

        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(callbackManager).handleWechatRefundCallback(headersCaptor.capture(), bodyCaptor.capture());
        assertEquals("sig", headersCaptor.getValue().get("Wechatpay-Signature"));
        assertEquals(CALLBACK_BODY, bodyCaptor.getValue(), "验签要求逐字节原文，不得被控制器改写");
    }

    @Test
    @DisplayName("验签/解密失败 — 500 + FAIL，且 message 为固定文案（不外泄内部细节）")
    void shouldReturnFailAnd500_whenSignatureInvalid() throws Exception {
        doThrow(new BizException(ErrorCode.ILLEGAL_REQUEST, "微信回调解密失败"))
                .when(callbackManager).handleWechatRefundCallback(anyMap(), anyString());

        MvcResult result = postCallback();
        String body = body(result);

        assertEquals(500, result.getResponse().getStatus(), "非 200 才会触发微信重推");
        assertEquals(WechatCallbackSupport.CODE_FAIL, JsonPath.read(body, "$.code"));
        assertEquals(WechatCallbackSupport.MESSAGE_FAIL, JsonPath.read(body, "$.message"));
        assertFalse(body.contains("微信回调解密失败"), "不得外泄内部失败原因: " + body);
    }

    @Test
    @DisplayName("重复通知幂等 — 退款单已成功（Manager 静默返回）：仍 200 + SUCCESS")
    void shouldReturnSuccess_whenDuplicateRefundNotification() throws Exception {
        MvcResult result = postCallback();
        String body = body(result);

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(WechatCallbackSupport.CODE_SUCCESS, JsonPath.read(body, "$.code"));
    }
}
