package org.icedamericanomall.integration.payment;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock 微信支付回调解析测试（ADR-033）—— Mock 口径：body 即明文 JSON，
 * 成功判定与字段提取与真实解密路径共用 {@link WechatCallbackDecoder#extract(String)}。
 */
@DisplayName("MockWechatPaymentClient — 回调明文 JSON 解析")
class MockWechatPaymentClientTest {

    private final WechatPaymentClient.MockWechatPaymentClient client =
            new WechatPaymentClient.MockWechatPaymentClient();

    private static final Map<String, String> HEADERS = Map.of("Wechatpay-Signature", "ignored-in-mock");

    @Test
    @DisplayName("trade_state=SUCCESS → success=true，out_trade_no 提取成功")
    void shouldParseSuccess_whenTradeStateSuccess() {
        WechatCallbackResult result = client.parseWechatCallback(HEADERS,
                "{\"out_trade_no\":\"PAY-1\",\"trade_state\":\"SUCCESS\",\"transaction_id\":\"TXN-1\"}");

        assertEquals("PAY-1", result.outTradeNo());
        assertTrue(result.success());
        assertEquals("TXN-1", result.channelTradeNo());
        assertEquals("SUCCESS", result.rawState());
    }

    @Test
    @DisplayName("result_code=SUCCESS → success=true（微信 v2/支付宝口径回落键）")
    void shouldParseSuccess_whenResultCodeSuccess() {
        WechatCallbackResult result = client.parseWechatCallback(HEADERS,
                "{\"out_trade_no\":\"PAY-1\",\"result_code\":\"SUCCESS\"}");

        assertTrue(result.success());
        assertEquals("PAY-1", result.outTradeNo());
    }

    @Test
    @DisplayName("trade_state=CLOSED → success=false，仍提取支付单号（由应用层决定不改状态）")
    void shouldMarkNotSuccess_whenTradeStateClosed() {
        WechatCallbackResult result = client.parseWechatCallback(HEADERS,
                "{\"out_trade_no\":\"PAY-1\",\"trade_state\":\"CLOSED\"}");

        assertFalse(result.success());
        assertEquals("PAY-1", result.outTradeNo());
        assertEquals("CLOSED", result.rawState());
    }

    @Test
    @DisplayName("body 非 JSON → BizException(ILLEGAL_REQUEST)（与真实解密失败同口径）")
    void shouldThrowIllegalRequest_whenBodyNotJson() {
        BizException ex = assertThrows(BizException.class,
                () -> client.parseWechatCallback(HEADERS, "out_trade_no=PAY-1"));

        assertEquals(ErrorCode.ILLEGAL_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("缺 out_trade_no → BizException(ILLEGAL_REQUEST)")
    void shouldThrowIllegalRequest_whenOutTradeNoMissing() {
        BizException ex = assertThrows(BizException.class,
                () -> client.parseWechatCallback(HEADERS, "{\"trade_state\":\"SUCCESS\"}"));

        assertEquals(ErrorCode.ILLEGAL_REQUEST, ex.getErrorCode());
    }

    // ==================== 退款（F1）与账单（F4） ====================

    @Test
    @DisplayName("退款回调 — 明文 JSON 提取 out_refund_no / refund_id / refund_status")
    void shouldParseRefundCallback_whenRefundStatusSuccess() {
        WechatRefundCallbackResult result = client.parseWechatRefundCallback(HEADERS,
                "{\"out_refund_no\":\"RFD-1\",\"refund_id\":\"5000000001\",\"refund_status\":\"SUCCESS\"}");

        assertEquals("RFD-1", result.outRefundNo());
        assertTrue(result.success());
        assertEquals("5000000001", result.refundId());
    }

    @Test
    @DisplayName("退款回调 — 缺 out_refund_no → BizException(ILLEGAL_REQUEST)")
    void shouldThrowIllegalRequest_whenOutRefundNoMissing() {
        BizException ex = assertThrows(BizException.class,
                () -> client.parseWechatRefundCallback(HEADERS, "{\"refund_status\":\"SUCCESS\"}"));

        assertEquals(ErrorCode.ILLEGAL_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("退款 — 受理即处理中（settled=false），等退款回调确认（与微信真实路径口径一致）")
    void shouldReturnProcessing_whenRefundRequested() {
        ChannelRefundResult result = client.refund("PAY-1", "RFD-1", 12800, 12800, "售后退款");

        assertFalse(result.settled(), "微信退款多数场景异步，Mock 亦保持处理中以便打通回调链路");
        assertEquals(WechatPaymentClient.MockWechatPaymentClient.MOCK_CHANNEL_REFUND_NO_PREFIX + "RFD-1",
                result.channelRefundNo());
    }

    @Test
    @DisplayName("JSAPI 下单 — Mock 返回六字段拉起参数集（packageVal 沿用 prepay_id= 协议前缀）")
    void shouldReturnJsapiParams_whenInitiateJsapiPayment() {
        JsapiPayResult result = client.initiateJsapiPayment("PAY-1", 11900, "订单支付", "openid-1");

        assertEquals(WechatPaymentClient.MockWechatPaymentClient.MOCK_APP_ID, result.appId());
        assertEquals(WechatPaymentClient.MockWechatPaymentClient.MOCK_SIGN_TYPE, result.signType());
        assertEquals(WechatPaymentClient.MockWechatPaymentClient.MOCK_PREPAY_ID_PREFIX + "PAY-1",
                result.packageVal());
        assertFalse(result.timeStamp().isBlank(), "timeStamp 不得为空（秒级时间戳字符串）");
        assertFalse(result.nonceStr().isBlank(), "nonceStr 不得为空");
        assertFalse(result.paySign().isBlank(), "paySign 不得为空");
    }

    @Test
    @DisplayName("账单下载 — 返回样例流水（金额单位：分）")
    void shouldDownloadSampleBill() {
        var items = client.billDownload("2026-10-02");

        assertEquals(2, items.size());
        assertEquals("MOCK-PAY-0001", items.get(0).outTradeNo());
        assertEquals(9900, items.get(0).amount());
        assertTrue(items.get(0).channelTxnId().startsWith("mock-txn-2026-10-02"));
    }
}
