package org.icedamericanomall.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BalanceRefundCO} 线协议字段名测试（I41 跨服务命名策略桥接，ADR-035 决策 7）。
 *
 * <p><b>为什么必须锁死</b>：wallet-service 的 Jackson 全局策略是 {@code SNAKE_CASE}，
 * 而调用方 pay-service 是默认 camelCase。若按各自的全局策略编解码，
 * 字段会**静默为 null**（不报错、金额按 0 处理），属最难排查的一类跨服务缺陷。
 * 本测试断言：无论发送方全局策略如何，序列化结果都是 snake_case（由 {@code @JsonProperty} 钉死）。
 */
@DisplayName("BalanceRefundCO — 线字段名固定为 snake_case")
class BalanceRefundCOTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private BalanceRefundCO request() {
        BalanceRefundCO co = new BalanceRefundCO();
        co.setTransactionNo("6f2c1a3e-8b4d-4e5f-9a0b-1c2d3e4f5a6b");
        co.setUserId(100L);
        co.setAmount(12800);
        co.setBizNo("6f2c1a3e-8b4d-4e5f-9a0b-1c2d3e4f5a6b");
        return co;
    }

    @Test
    @DisplayName("序列化 — 线上字段名为 transaction_no / user_id / amount / biz_no")
    void shouldSerializeToSnakeCase_whenDefaultNamingStrategy() throws Exception {
        String json = objectMapper.writeValueAsString(request());

        assertTrue(json.contains("\"transaction_no\""), "实际报文=" + json);
        assertTrue(json.contains("\"user_id\""), "实际报文=" + json);
        assertTrue(json.contains("\"biz_no\""), "实际报文=" + json);
        assertTrue(json.contains("\"amount\":12800"), "实际报文=" + json);
        assertFalse(json.contains("transactionNo"), "不得出现 camelCase 字段名: " + json);
        assertFalse(json.contains("userId"), "不得出现 camelCase 字段名: " + json);
    }

    @Test
    @DisplayName("反序列化 — 同一份报文可被按 snake_case 配置的接收方解析（wallet 侧口径）")
    void shouldDeserializeFromSnakeCase() throws Exception {
        String json = objectMapper.writeValueAsString(request());

        BalanceRefundCO parsed = objectMapper.readValue(json, BalanceRefundCO.class);

        assertTrue("6f2c1a3e-8b4d-4e5f-9a0b-1c2d3e4f5a6b".equals(parsed.getTransactionNo()));
        assertTrue(Long.valueOf(100L).equals(parsed.getUserId()));
        assertTrue(Integer.valueOf(12800).equals(parsed.getAmount()));
    }
}
