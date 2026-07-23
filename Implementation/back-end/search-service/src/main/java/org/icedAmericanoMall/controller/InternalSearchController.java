package org.icedAmericanoMall.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.domain.entity.ProductEntity;
import org.icedAmericanoMall.domain.vo.ProductSearchVO;
import org.icedAmericanoMall.mapper.ProductMapper;
import org.icedAmericanoMall.mapper.SkuMapper;
import org.icedAmericanoMall.domain.entity.SkuEntity;
import org.icedAmericanoMall.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 内部搜索管理接口 — 索引重建等运维操作。供 XXL-Job / Canal 调用。
 */
@Slf4j
@RestController
@RequestMapping("/internal/search")
@RequiredArgsConstructor
public class InternalSearchController {

    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final SearchService searchService;

    @Autowired(required = false)
    private ElasticsearchClient esClient;

    /**
     * 全量重建商品索引 — 从主库拉取所有上架商品写入 ES。
     * 仅在 ES 启用时有效（esClient != null）。
     */
    @PostMapping("/reindex")
    public Map<String, Object> reindex() {
        // 1. 拉取所有上架商品
        List<ProductEntity> products = productMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductEntity>()
                        .eq(ProductEntity::getStatus, 1));

        // 2. 获取最低 SKU 价格
        Map<Long, Integer> priceMap = new HashMap<>();
        for (ProductEntity p : products) {
            List<SkuEntity> skus = skuMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SkuEntity>()
                            .eq(SkuEntity::getProductId, p.getId())
                            .eq(SkuEntity::getStatus, 1));
            priceMap.put(p.getId(), skus.stream().mapToInt(SkuEntity::getPrice).min().orElse(0));
        }

        // 3. 写入 ES
        if (esClient != null) {
            try {
                // 先删除旧索引
                try { esClient.indices().delete(d -> d.index("products")); } catch (Exception ignored) {}

                BulkRequest.Builder bulk = new BulkRequest.Builder();
                for (ProductEntity p : products) {
                    Map<String, Object> doc = new LinkedHashMap<>();
                    doc.put("id", p.getId());
                    doc.put("productId", p.getProductId());
                    doc.put("name", p.getName());
                    doc.put("description", p.getDescription());
                    doc.put("brand", p.getBrand());
                    doc.put("categoryId", p.getCategoryId());
                    doc.put("price", priceMap.getOrDefault(p.getId(), 0));
                    doc.put("mainImage", p.getMainImage());
                    doc.put("soldCount", p.getSoldCount());
                    bulk.operations(op -> op.index(idx -> idx.index("products").id(String.valueOf(p.getId())).document(doc)));
                }
                var response = esClient.bulk(bulk.build());
                long errors = response.items().stream().filter(i -> i.error() != null).count();
                log.info("ES reindex complete: {} products, {} errors", products.size(), errors);
            } catch (Exception e) {
                log.error("ES reindex failed", e);
                return Map.of("status", "error", "message", e.getMessage(), "count", products.size());
            }
        } else {
            log.info("ES not enabled, reindex skipped. {} products available.", products.size());
        }

        return Map.of("status", "ok", "count", products.size(),
                "esEnabled", esClient != null);
    }

    /**
     * V2.5.2: 向量语义搜索 — 供 ai-service RAG 管线调用。
     * <p>
     * 接受查询向量（double[]），返回 Top-K 语义相似商品。
     * 当 ES 未启用时返回空列表（降级处理）。
     */
    /** V3.2: 向量搜索请求 — 强类型 Record 替代 Map */
    public record VectorSearchRequest(List<Double> embedding, int size) {
        public int size() { return size > 0 ? size : 10; }
    }

    @PostMapping("/vector")
    public List<ProductSearchVO> vectorSearch(@RequestBody VectorSearchRequest req) {
        double[] embedding = req.embedding().stream().mapToDouble(Double::doubleValue).toArray();
        return searchService.vectorSearch(embedding, req.size());
    }
}
