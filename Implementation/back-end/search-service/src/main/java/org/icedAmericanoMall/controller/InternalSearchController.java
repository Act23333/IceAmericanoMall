package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.vo.ProductSearchVO;
import org.icedAmericanoMall.service.SearchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 内部搜索管理接口 — 索引重建等运维操作。
 * <p>
 * DDD 分层: Controller 仅做参数解析 + 委托 Service，业务逻辑全在 Service 层。
 */
@RestController
@RequestMapping("/internal/search")
@RequiredArgsConstructor
public class InternalSearchController {

    private final SearchService searchService;

    public record VectorSearchRequest(List<Double> embedding, int size) {
        public int size() { return size > 0 ? size : 10; }
    }

    @PostMapping("/reindex")
    public Map<String, Object> reindex() {
        return searchService.reindex();
    }

    @PostMapping("/vector")
    public List<ProductSearchVO> vectorSearch(@RequestBody VectorSearchRequest req) {
        double[] embedding = req.embedding().stream().mapToDouble(Double::doubleValue).toArray();
        return searchService.vectorSearch(embedding, req.size());
    }
}
