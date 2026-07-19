package org.icedAmericanoMall.client;

import org.icedAmericanoMall.config.DefaultFeignConfig;
import org.icedAmericanoMall.dto.ProductSearchResult;
import org.noLazy.common.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign client for search-service product search endpoints.
 * Used by ai-service SearchTool for Agent-triggered product queries.
 */
@FeignClient(
    name = "search-service",
    path = "/api/search",
    configuration = DefaultFeignConfig.class
)
public interface SearchClient {

    /**
     * 商品搜索（关键词 + 分页）。
     * <p>
     * 对应 search-service SearchController.search()。
     *
     * @param keyword  搜索关键词
     * @param size     返回条数（默认 5）
     * @return 搜索结果封装在 Result 中
     */
    @GetMapping("/product")
    Result<List<ProductSearchResult>> searchProducts(
        @RequestParam("keyword") String keyword,
        @RequestParam(value = "size", defaultValue = "5") int size
    );
}
