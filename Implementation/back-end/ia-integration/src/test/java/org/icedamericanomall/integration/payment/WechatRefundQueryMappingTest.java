package org.icedamericanomall.integration.payment;

import com.wechat.pay.java.service.refund.model.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 微信退款查询（悬挂退款补偿，ADR-035 风险收敛）—— 状态归一化与 Mock 口径测试。
 *
 * <p>真实实现的查询链路依赖微信证书配置（{@code @PostConstruct} 初始化），此处聚焦
 * 可纯静态验证的两块：{@code refund_status} → {@link ChannelRefundState} 归一化映射、
 * Mock 客户端的恒「处理中」口径。真实 HTTP/SDK 链路由联调环境覆盖。
 */
@DisplayName("微信退款查询 — 状态归一化与 Mock 口径")
class WechatRefundQueryMappingTest {

    private final WechatPaymentClient.MockWechatPaymentClient mockClient =
            new WechatPaymentClient.MockWechatPaymentClient();

    @ParameterizedTest(name = "refund_status={0} → FAILED（终止失败）")
    @EnumSource(value = Status.class, names = {"CLOSED", "ABNORMAL"})
    @DisplayName("CLOSED / ABNORMAL → 归一化为 FAILED")
    void shouldMapFailed_whenWechatStatusTerminalFailure(Status status) {
        assertEquals(ChannelRefundState.FAILED, WechatPaymentClient.toChannelRefundState(status));
    }

    @Test
    @DisplayName("SUCCESS → 归一化为 SUCCESS")
    void shouldMapSuccess_whenWechatStatusSuccess() {
        assertEquals(ChannelRefundState.SUCCESS, WechatPaymentClient.toChannelRefundState(Status.SUCCESS));
    }

    @Test
    @DisplayName("PROCESSING → 归一化为 PROCESSING")
    void shouldMapProcessing_whenWechatStatusProcessing() {
        assertEquals(ChannelRefundState.PROCESSING, WechatPaymentClient.toChannelRefundState(Status.PROCESSING));
    }

    @Test
    @DisplayName("Mock 查询 — 恒返回处理中（渠道退款单号沿用 mock-rfd- 前缀，不模拟异步推进）")
    void shouldReturnProcessing_whenMockQueryRefund() {
        ChannelRefundQueryResult result = mockClient.queryRefund("RFD-1");

        assertEquals("mock-rfd-RFD-1", result.refundId());
        assertEquals(ChannelRefundState.PROCESSING, result.state());
    }
}
