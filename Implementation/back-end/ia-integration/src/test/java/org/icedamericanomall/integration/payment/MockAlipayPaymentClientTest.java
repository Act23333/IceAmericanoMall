package org.icedamericanomall.integration.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MockAlipayPaymentClient 单元测试 —— 虚拟支付链接 + 验签恒通过。
 */
@DisplayName("MockAlipayPaymentClient 单元测试")
class MockAlipayPaymentClientTest {

    private final MockAlipayPaymentClient client = new MockAlipayPaymentClient();

    @Test
    @DisplayName("initiatePayment — 返回含订单号的虚拟支付链接")
    void shouldReturnMockPayUrl() {
        String url = client.initiatePayment("ORD-9", 12900, "订单支付");
        assertNotNull(url);
        assertTrue(url.contains("ORD-9"));
        assertTrue(url.startsWith("https://mock-alipay"));
    }

    @Test
    @DisplayName("verifyCallback — Mock 恒通过；queryStatus 恒 SUCCESS")
    void shouldVerifyAndQuery() {
        assertTrue(client.verifyCallback(Map.of("out_trade_no", "PAY-1")));
        assertEquals("SUCCESS", client.queryStatus("PAY-1"));
    }

    @Test
    @DisplayName("退款 — 同步成功（settled=true，与支付宝同步返回退款结果的语义一致）")
    void shouldSettleRefundSynchronously() {
        ChannelRefundResult result = client.refund("PAY-1", "RFD-1", 12800, 12800, "售后退款");

        assertTrue(result.settled());
        assertEquals(MockAlipayPaymentClient.MOCK_CHANNEL_REFUND_NO_PREFIX + "RFD-1",
                result.channelRefundNo());
    }

    @Test
    @DisplayName("账单下载 — 返回样例流水（金额单位：分）")
    void shouldDownloadSampleBill() {
        var items = client.billDownload("2026-10-02");

        assertEquals(2, items.size());
        assertEquals("MOCK-ALI-PAY-0001", items.get(0).outTradeNo());
        assertEquals(6600, items.get(0).amount());
    }
}
