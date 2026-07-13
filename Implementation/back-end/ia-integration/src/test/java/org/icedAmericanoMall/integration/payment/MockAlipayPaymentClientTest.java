package org.icedAmericanoMall.integration.payment;

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
}
