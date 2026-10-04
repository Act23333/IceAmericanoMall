package org.icedamericanomall.ai.infrastructure.adapter.tool;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.ai.domain.model.product.ProductHit;
import org.icedamericanomall.ai.domain.port.SearchPort;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LangChain4j @Tool — 商品搜索。
 * <p>
 * 仅依赖领域端口 {@link SearchPort}，Feign 调用与报文装配在基础设施层（防腐层）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchTool {

    /** Agent 每次搜索返回的商品条数。 */
    private static final int SEARCH_SIZE = 5;

    private final SearchPort searchPort;

    /**
     * 搜索商品并返回 LLM 可读的文本列表。
     *
     * @param keyword 搜索关键词
     * @return 商品摘要文本；无结果或搜索服务不可用返回友好提示
     */
    @Tool("搜索冰美城商城的商品，返回名称、价格、销量")
    public String searchProducts(String keyword) {
        try {
            List<ProductHit> hits = searchPort.searchProducts(keyword, SEARCH_SIZE);
            if (hits.isEmpty()) {
                return "未找到与「" + keyword + "」相关的商品，试试换个关键词？";
            }
            StringBuilder sb = new StringBuilder();
            for (ProductHit hit : hits) {
                sb.append(String.format("- %s (ID:%s) ¥%.2f 销量:%s%n",
                        hit.name(), hit.productId(), centsToYuan(hit.priceCents()), hit.soldCount()));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("SearchTool error for keyword={}: {}", keyword, e.getMessage());
            return "搜索服务暂不可用，请稍后重试";
        }
    }

    /** 金额分 → 元（保留两位小数）。 */
    private static double centsToYuan(Integer cents) {
        return cents == null ? 0 : cents / 100.0;
    }
}
