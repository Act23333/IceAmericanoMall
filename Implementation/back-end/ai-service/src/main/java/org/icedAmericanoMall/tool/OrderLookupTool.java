package org.icedAmericanoMall.tool;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

/**
 * LangChain4j @Tool — 订单查询。被客服 Agent 调用。
 */
@Slf4j
@Component
public class OrderLookupTool {
    private final RestTemplate restTemplate = new RestTemplate();

    @Tool("根据订单号查询订单状态、金额等信息")
    public String lookupOrder(String orderNo) {
        try {
            String url = "http://trade-service/api/trade/order/" + orderNo;
            var resp = restTemplate.getForObject(url, Map.class);
            if (resp != null && resp.get("data") != null) {
                Map<String, Object> order = (Map<String, Object>) resp.get("data");
                return String.format("订单 %s: 状态=%s, 金额=¥%.2f",
                        order.get("orderNo"), order.get("status"),
                        ((Number) order.getOrDefault("totalAmount", 0)).doubleValue() / 100);
            }
            return "未找到订单 " + orderNo;
        } catch (Exception e) {
            log.warn("OrderLookupTool error", e);
            return "查询失败: " + e.getMessage();
        }
    }
}
