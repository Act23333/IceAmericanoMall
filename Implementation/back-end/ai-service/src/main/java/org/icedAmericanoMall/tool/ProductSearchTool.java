package org.icedAmericanoMall.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Slf4j
@Component
public class ProductSearchTool {
    private final RestTemplate restTemplate = new RestTemplate();

    public String search(String keyword, Long categoryId) {
        try {
            String url = "http://search-service/api/search/product?keyword=" + keyword
                    + (categoryId != null ? "&categoryId=" + categoryId : "") + "&size=5";
            var resp = restTemplate.getForObject(url, Map.class);
            if (resp != null && resp.get("data") != null) {
                Map<String, Object> data = (Map<String, Object>) resp.get("data");
                List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
                if (records == null || records.isEmpty()) return "未找到相关商品";
                StringBuilder sb = new StringBuilder();
                for (var r : records) {
                    sb.append(String.format("- %s (ID:%s) ¥%.2f 销量:%s\n",
                            r.get("name"), r.get("productId"),
                            ((Number) r.get("price")).doubleValue() / 100,
                            r.get("soldCount")));
                }
                return sb.toString();
            }
            return "搜索服务暂时不可用";
        } catch (Exception e) {
            log.warn("ProductSearchTool error", e);
            return "搜索失败: " + e.getMessage();
        }
    }
}
