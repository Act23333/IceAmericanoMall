package org.icedamericanomall.tool;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.SearchClient;
import org.noLazy.common.domain.Result;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * LangChain4j @Tool — 商品搜索，通过 Feign Client 调用 search-service。
 * <p>
 * V2.5: 使用 {@link SearchClient} (ia-api Feign) 替代 {@code RestTemplate} 直连，
 * 天然集成 Nacos 负载均衡 + Sentinel 熔断 + X-User-Id 传播。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchTool {

    private final SearchClient searchClient;

    @Tool("搜索冰美城商城的商品，返回名称、价格、销量")
    public String searchProducts(String keyword) {
        try {
            Result<Map<String, Object>> result = searchClient.searchProducts(keyword, 5);
            if (result == null || result.getData() == null) {
                return "搜索服务暂不可用";
            }
            Object rawRecords = result.getData().get("records");
            if (!(rawRecords instanceof List<?> list)) {
                return "未找到与「" + keyword + "」相关的商品，试试换个关键词？";
            }
            if (list.isEmpty()) {
                return "未找到与「" + keyword + "」相关的商品，试试换个关键词？";
            }
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (!(item instanceof Map<?,?> m)) continue;
                Map<String, Object> r = castMap(m);
                Object price = r.get("price");
                double priceYuan = price instanceof Number ? ((Number) price).doubleValue() / 100 : 0;
                sb.append(String.format("- %s (ID:%s) ¥%.2f 销量:%s%n",
                        r.get("name"), r.get("productId"),
                        priceYuan, r.get("soldCount")));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("SearchTool error for keyword={}: {}", keyword, e.getMessage());
            return "搜索服务暂不可用，请稍后重试";
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> castMap(Map<?,?> m) {
        return (Map) m;
    }
}
