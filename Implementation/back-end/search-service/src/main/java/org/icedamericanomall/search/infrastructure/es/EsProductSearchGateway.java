package org.icedamericanomall.search.infrastructure.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.search.domain.gateway.ProductSearchGateway;
import org.icedamericanomall.search.domain.model.search.ProductSearchHit;
import org.icedamericanomall.search.domain.model.search.SearchPage;
import org.icedamericanomall.search.domain.model.search.SearchQuery;
import org.icedamericanomall.search.infrastructure.persistence.ProductMapper;
import org.icedamericanomall.search.infrastructure.persistence.ProductPO;
import org.icedamericanomall.search.infrastructure.persistence.SkuMapper;
import org.icedamericanomall.search.infrastructure.persistence.SkuPO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ElasticSearch 检索网关 —— {@code search.elasticsearch.enabled=true} 时激活。
 * ES 7.17 + IK 中文分词，NativeQuery + MatchQuery；kNN 向量检索支持。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "true")
public class EsProductSearchGateway implements ProductSearchGateway {

    /** 在售状态（与 DB 网关同口径）。 */
    private static final int STATUS_ON_SALE = 1;

    private final ElasticsearchOperations esOps;
    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final ElasticsearchClient esClient;

    @Override
    public SearchPage<ProductSearchHit> search(SearchQuery query) {
        var bool = new BoolQuery.Builder();

        if (query.hasKeyword()) {
            bool.should(Query.of(q -> q.match(MatchQuery.of(m -> m.field("name").query(query.keyword())))));
            bool.should(Query.of(q -> q.match(MatchQuery.of(m -> m.field("description").query(query.keyword())))));
            bool.minimumShouldMatch("1");
        } else {
            bool.must(Query.of(q -> q.matchAll(m -> m)));
        }

        if (query.categoryId() != null) {
            bool.filter(Query.of(q -> q.term(t -> t.field("categoryId").value(query.categoryId()))));
        }

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(Query.of(q -> q.bool(bool.build())))
                .withPageable(PageRequest.of(query.page() - 1, query.size()))
                .build();

        SearchHits<ProductDocument> hits = esOps.search(nativeQuery, ProductDocument.class);

        List<ProductSearchHit> records = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(EsProductSearchGateway::toHit)
                .toList();
        return new SearchPage<>(records, hits.getTotalHits(), query.page(), query.size());
    }

    @Override
    public List<ProductSearchHit> vectorSearch(double[] embedding, int size) {
        float[] floatEmbedding = new float[embedding.length];
        for (int i = 0; i < embedding.length; i++) {
            floatEmbedding[i] = (float) embedding[i];
        }

        Query knn = Query.of(q -> q.knn(KnnQuery.of(k -> k
                .field("embedding")
                .queryVector(toFloatList(floatEmbedding))
                .k(size)
                .numCandidates(size * 2))));

        NativeQuery query = NativeQuery.builder()
                .withQuery(knn)
                .withPageable(PageRequest.of(0, size))
                .build();

        SearchHits<ProductDocument> hits = esOps.search(query, ProductDocument.class);
        return hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(EsProductSearchGateway::toHit)
                .toList();
    }

    @Override
    public Map<String, Object> reindex() {
        List<ProductPO> products = productMapper.selectList(
                new LambdaQueryWrapper<ProductPO>().eq(ProductPO::getStatus, STATUS_ON_SALE));
        Map<Long, Integer> priceMap = new HashMap<>();
        for (ProductPO p : products) {
            List<SkuPO> skus = skuMapper.selectList(
                    new LambdaQueryWrapper<SkuPO>()
                            .eq(SkuPO::getProductId, p.getId())
                            .eq(SkuPO::getStatus, STATUS_ON_SALE));
            priceMap.put(p.getId(), skus.stream().mapToInt(SkuPO::getPrice).min().orElse(0));
        }

        try {
            try {
                esClient.indices().delete(d -> d.index("products"));
            } catch (Exception ignored) {
                // 索引不存在时删除报错可忽略，随后重建
            }
            BulkRequest.Builder bulk = new BulkRequest.Builder();
            for (ProductPO p : products) {
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
                bulk.operations(op -> op.index(idx -> idx.index("products")
                        .id(String.valueOf(p.getId())).document(doc)));
            }
            var resp = esClient.bulk(bulk.build());
            long errors = resp.items().stream().filter(i -> i.error() != null).count();
            log.info("ES reindex complete: {} products, {} errors", products.size(), errors);
        } catch (Exception e) {
            log.error("ES reindex failed", e);
            return Map.of("status", "error", "message", e.getMessage(), "count", products.size());
        }
        return Map.of("status", "ok", "count", products.size(), "esEnabled", true);
    }

    private static ProductSearchHit toHit(ProductDocument doc) {
        return new ProductSearchHit(doc.getId(), doc.getProductId(), doc.getCategoryId(), doc.getName(),
                doc.getDescription(), doc.getBrand(), doc.getMainImage(), doc.getPrice(), doc.getSoldCount());
    }

    private static List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float f : array) {
            list.add(f);
        }
        return list;
    }
}
