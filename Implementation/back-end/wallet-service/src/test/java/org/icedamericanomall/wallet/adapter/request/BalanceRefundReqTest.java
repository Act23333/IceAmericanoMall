package org.icedamericanomall.wallet.adapter.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link BalanceRefundReq} 线协议字段名测试（I41，ADR-035 决策 7）。
 *
 * <p>本服务的 Jackson 全局策略为 {@code SNAKE_CASE}；测试用同一策略的 ObjectMapper
 * 反序列化 I41 的报文，断言「调用方发来的 snake_case 报文能正确落到字段上」。
 * 入参对象上的 {@code @JsonProperty} 是这层桥接的唯一依据（不依赖任一服务的全局配置）。
 */
@DisplayName("BalanceRefundReq — 接收 I41 snake_case 报文")
class BalanceRefundReqTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Test
    @DisplayName("反序列化 — transaction_no / user_id / amount / biz_no 全部落到字段")
    void shouldBindSnakeCasePayload() throws Exception {
        String json = """
                {"transaction_no":"RFD-1","user_id":100,"amount":12800,"biz_no":"RFD-1"}""";

        BalanceRefundReq req = objectMapper.readValue(json, BalanceRefundReq.class);

        assertEquals("RFD-1", req.getTransactionNo());
        assertEquals(100L, req.getUserId());
        assertEquals(12800, req.getAmount());
        assertEquals("RFD-1", req.getBizNo());
    }
}
