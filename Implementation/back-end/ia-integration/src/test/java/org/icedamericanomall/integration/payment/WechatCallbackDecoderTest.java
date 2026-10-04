package org.icedamericanomall.integration.payment;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 微信 v3 回调解码器测试 —— 锁死「resource 解密规范」（ADR-033）。
 *
 * <p>测试用固定 APIv3 密钥，按微信规范现场生成密文固件
 * （{@code ciphertext = Base64(密文 ‖ 认证标签)}，{@code IV = nonce 字节}，
 * {@code AAD = associated_data 字节}），再走真实解码链路断言 {@code out_trade_no} 提取成功。
 * 这条链路正是存量 P0 断链所在：修复前回调参数里根本没有 {@code out_trade_no}。
 */
@DisplayName("微信 v3 回调解码 — 验签后解密 resource 提取强类型结果")
class WechatCallbackDecoderTest {

    /** 固定 APIv3 密钥（32 字节，UTF-8）。 */
    private static final String API_V3_KEY = "0123456789abcdef0123456789abcdef";
    private static final String OTHER_KEY = "fedcba9876543210fedcba9876543210";
    /** IV 必须是 12 字节（微信 nonce 固定 12 字符）。 */
    private static final String NONCE = "abcd12345678";
    private static final String AAD = "transaction";
    private static final String PAY_ORDER_NO = "PAY-20261003-0001";
    private static final String TRANSACTION_ID = "4200001234202610031234567890";
    private static final String REFUND_NO = "6f2c1a3e-8b4d-4e5f-9a0b-1c2d3e4f5a6b";
    private static final String CHANNEL_REFUND_ID = "50000000382026100312345678901";

    /** 明文报文（微信解密产物）—— 字段口径与线上一致。 */
    private static String plaintext(String tradeState) {
        return """
                {"mchid":"1900000109","out_trade_no":"%s","transaction_id":"%s",
                 "trade_type":"NATIVE","trade_state":"%s","success_time":"2026-10-03T10:00:00+08:00",
                 "amount":{"total":11900,"payer_total":11900,"currency":"CNY"}}"""
                .formatted(PAY_ORDER_NO, TRANSACTION_ID, tradeState);
    }

    private static String outerBody(String key, String nonce, String aad, String plainJson) throws Exception {
        return """
                {"id":"EV-20261003-1","create_time":"2026-10-03T10:00:00+08:00",
                 "event_type":"TRANSACTION.SUCCESS","resource_type":"encrypt-resource",
                 "resource":{"algorithm":"AEAD_AES_256_GCM","original_type":"transaction",
                             "ciphertext":"%s","nonce":"%s","associated_data":"%s"}}"""
                .formatted(encrypt(key, nonce, aad, plainJson), nonce, aad);
    }

    /** 按微信 v3 规范加密：AES-256-GCM，IV=nonce 字节，AAD=associated_data 字节，输出 Base64(密文 ‖ 标签)。 */
    private static String encrypt(String key, String nonce, String aad, String plainJson) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
                new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
        if (!aad.isEmpty()) {
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        }
        return Base64.getEncoder().encodeToString(cipher.doFinal(plainJson.getBytes(StandardCharsets.UTF_8)));
    }

    // ==================== 真实解密路径（含 P0 断链回归） ====================

    @Test
    @DisplayName("真实链路 — 固定 APIv3 密钥解密 resource：提取 out_trade_no / transaction_id / SUCCESS")
    void shouldExtractOutTradeNo_whenResourceDecrypted() throws Exception {
        String body = outerBody(API_V3_KEY, NONCE, AAD, plaintext("SUCCESS"));

        WechatCallbackResult result = WechatCallbackDecoder.decode(API_V3_KEY, body);

        assertEquals(PAY_ORDER_NO, result.outTradeNo(), "存量 P0：out_trade_no 必须从解密后的报文提取");
        assertTrue(result.success());
        assertEquals(TRANSACTION_ID, result.channelTradeNo());
        assertEquals("SUCCESS", result.rawState());
    }

    @Test
    @DisplayName("真实链路 — 无 AAD（associated_data 为空串）时仍可解密")
    void shouldDecrypt_whenAssociatedDataEmpty() throws Exception {
        String body = outerBody(API_V3_KEY, NONCE, "", plaintext("SUCCESS"));

        WechatCallbackResult result = WechatCallbackDecoder.decode(API_V3_KEY, body);

        assertEquals(PAY_ORDER_NO, result.outTradeNo());
        assertTrue(result.success());
    }

    @Test
    @DisplayName("真实链路 — 非成功终态（CLOSED）：success=false 但 out_trade_no 仍可提取")
    void shouldMarkNotSuccess_whenTradeStateClosed() throws Exception {
        String body = outerBody(API_V3_KEY, NONCE, AAD, plaintext("CLOSED"));

        WechatCallbackResult result = WechatCallbackDecoder.decode(API_V3_KEY, body);

        assertFalse(result.success());
        assertEquals("CLOSED", result.rawState());
        assertEquals(PAY_ORDER_NO, result.outTradeNo());
    }

    @Test
    @DisplayName("密钥不匹配 — GCM 认证标签校验失败 → BizException(ILLEGAL_REQUEST)")
    void shouldThrowIllegalRequest_whenApiV3KeyMismatch() throws Exception {
        String body = outerBody(OTHER_KEY, NONCE, AAD, plaintext("SUCCESS"));

        BizException ex = assertThrows(BizException.class, () -> WechatCallbackDecoder.decode(API_V3_KEY, body));

        assertEquals(ErrorCode.ILLEGAL_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("AAD 被篡改 — 认证失败 → BizException(ILLEGAL_REQUEST)（证明 AAD 真的参与校验）")
    void shouldThrowIllegalRequest_whenAssociatedDataTampered() throws Exception {
        String ciphertext = encrypt(API_V3_KEY, NONCE, AAD, plaintext("SUCCESS"));

        assertThrows(BizException.class,
                () -> WechatCallbackDecoder.decryptResource(API_V3_KEY, ciphertext, NONCE, "tampered"));
    }

    @Test
    @DisplayName("IV 不被规范一致使用 — 用其它 nonce 解密 → BizException(ILLEGAL_REQUEST)（证明 IV=nonce 字节）")
    void shouldThrowIllegalRequest_whenNonceNotUsedAsIv() throws Exception {
        String ciphertext = encrypt(API_V3_KEY, NONCE, AAD, plaintext("SUCCESS"));

        assertThrows(BizException.class,
                () -> WechatCallbackDecoder.decryptResource(API_V3_KEY, ciphertext, "87654321dcba", AAD));
    }

    // ==================== 报文非法 ====================

    @Test
    @DisplayName("报文非 JSON → BizException(ILLEGAL_REQUEST)")
    void shouldThrowIllegalRequest_whenBodyNotJson() {
        BizException ex = assertThrows(BizException.class,
                () -> WechatCallbackDecoder.decode(API_V3_KEY, "not-a-json-body"));

        assertEquals(ErrorCode.ILLEGAL_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("缺少 resource 节点 → BizException(ILLEGAL_REQUEST)")
    void shouldThrowIllegalRequest_whenResourceMissing() {
        BizException ex = assertThrows(BizException.class,
                () -> WechatCallbackDecoder.decode(API_V3_KEY, "{\"id\":\"EV-1\"}"));

        assertEquals(ErrorCode.ILLEGAL_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("解密成功但缺 out_trade_no → BizException(ILLEGAL_REQUEST)")
    void shouldThrowIllegalRequest_whenOutTradeNoMissing() throws Exception {
        String body = outerBody(API_V3_KEY, NONCE, AAD, "{\"trade_state\":\"SUCCESS\"}");

        BizException ex = assertThrows(BizException.class, () -> WechatCallbackDecoder.decode(API_V3_KEY, body));

        assertEquals(ErrorCode.ILLEGAL_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("明文提取 — trade_state 缺省时回落 result_code")
    void shouldFallbackToResultCode_whenTradeStateAbsent() {
        WechatCallbackResult result = WechatCallbackDecoder.extract(
                "{\"out_trade_no\":\"PAY-1\",\"result_code\":\"SUCCESS\"}");

        assertTrue(result.success());
        assertEquals("SUCCESS", result.rawState());
    }

    // ==================== 退款回调（F1 PAY6） ====================

    /** 退款通知明文报文（微信解密产物）—— 字段口径与线上一致。 */
    private static String refundPlaintext(String refundStatus) {
        return """
                {"mchid":"1900000109","out_trade_no":"%s","out_refund_no":"%s","refund_id":"%s",
                 "refund_status":"%s","success_time":"2026-10-03T10:05:00+08:00",
                 "amount":{"total":12800,"refund":12800,"payer_total":12800,"payer_refund":12800}}"""
                .formatted(PAY_ORDER_NO, REFUND_NO, CHANNEL_REFUND_ID, refundStatus);
    }

    @Test
    @DisplayName("退款真实链路 — 解密 resource 提取 out_refund_no / refund_id / refund_status=SUCCESS")
    void shouldExtractOutRefundNo_whenRefundResourceDecrypted() throws Exception {
        String body = outerBody(API_V3_KEY, NONCE, AAD, refundPlaintext("SUCCESS"));

        WechatRefundCallbackResult result = WechatCallbackDecoder.decodeRefund(API_V3_KEY, body);

        assertEquals(REFUND_NO, result.outRefundNo());
        assertTrue(result.success());
        assertEquals(CHANNEL_REFUND_ID, result.refundId());
        assertEquals("SUCCESS", result.rawRefundStatus());
    }

    @Test
    @DisplayName("退款真实链路 — refund_status=CLOSED → success=false（应用层不改状态）")
    void shouldMarkNotSuccess_whenRefundClosed() throws Exception {
        String body = outerBody(API_V3_KEY, NONCE, AAD, refundPlaintext("CLOSED"));

        WechatRefundCallbackResult result = WechatCallbackDecoder.decodeRefund(API_V3_KEY, body);

        assertFalse(result.success());
        assertEquals("CLOSED", result.rawRefundStatus());
        assertEquals(REFUND_NO, result.outRefundNo());
    }

    @Test
    @DisplayName("退款回调 — 与支付回调共用验签/解密基建：密钥不匹配同样 ILLEGAL_REQUEST")
    void shouldThrowIllegalRequest_whenRefundApiV3KeyMismatch() throws Exception {
        String body = outerBody(OTHER_KEY, NONCE, AAD, refundPlaintext("SUCCESS"));

        BizException ex = assertThrows(BizException.class,
                () -> WechatCallbackDecoder.decodeRefund(API_V3_KEY, body));

        assertEquals(ErrorCode.ILLEGAL_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("退款明文提取 — 缺 out_refund_no → BizException(ILLEGAL_REQUEST)")
    void shouldThrowIllegalRequest_whenOutRefundNoMissing() {
        BizException ex = assertThrows(BizException.class,
                () -> WechatCallbackDecoder.extractRefund("{\"refund_status\":\"SUCCESS\"}"));

        assertEquals(ErrorCode.ILLEGAL_REQUEST, ex.getErrorCode());
    }
}
