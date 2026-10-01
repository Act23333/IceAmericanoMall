package org.icedamericanomall.client;

import org.icedamericanomall.config.DefaultFeignConfig;
import org.icedamericanomall.fallback.SearchClientFallback;
import org.icedamericanomall.common.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Feign client for search-service product search endpoints.
 * Used by ai-service SearchTool for Agent-triggered product queries.
 * <p>
 * Returns {@code Result<Map>} because search-service response wraps MyBatis-Plus
 * {@code Page<ProductSearchVO>} which is deserialized as a Map with "records" key.
 *
 * <p>降级语义：<b>fail-soft</b>（返回非成功 {@code Result}，{@code data} 为 null）。
 * 调用方把 {@code data == null} 解释为「搜索服务暂不可用」，与下游故障语义一致，
 * 且搜索是 AI 助手的增强能力，不应因降级中断整轮对话。
 * 详见 {@link SearchClientFallback}。
 */
@FeignClient(
    name = "search-service",
    path = "/api/search",
    contextId = "search",
    configuration = DefaultFeignConfig.class,
    fallbackFactory = SearchClientFallback.class
)
public interface SearchClient {

    /**
     * 商品搜索（关键词 + 分页）。
     * <p>
     * 对应 search-service SearchController.search()。
     *
     * @param keyword  搜索关键词
     * @param size     返回条数（默认 5）
     * @return Result.data 为包含 "records" 列表的 Map
     */
    @GetMapping("/product")
    Result<Map<String, Object>> searchProducts(
        @RequestParam("keyword") String keyword,
        @RequestParam(value = "size", defaultValue = "5") int size
    );
}
