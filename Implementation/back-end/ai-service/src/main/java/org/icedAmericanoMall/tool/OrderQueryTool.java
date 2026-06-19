package org.icedAmericanoMall.tool;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

/** 订单查询工具 — 调用 trade-service 查询订单状态 */
@Component
@RequiredArgsConstructor
public class OrderQueryTool {
    private final RestTemplate restTemplate = new RestTemplate();

    public String query(String orderNo, Long userId) {
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
            return "订单查询失败: " + e.getMessage();
        }
    }
}
