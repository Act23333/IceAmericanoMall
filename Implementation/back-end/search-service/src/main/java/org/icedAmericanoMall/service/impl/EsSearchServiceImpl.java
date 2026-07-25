package org.icedAmericanoMall.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedAmericanoMall.domain.entity.ProductEntity;
import org.icedAmericanoMall.domain.entity.SkuEntity;
import org.icedAmericanoMall.domain.vo.ProductSearchVO;
import org.icedAmericanoMall.mapper.ProductMapper;
import org.icedAmericanoMall.mapper.SkuMapper;
import org.icedAmericanoMall.service.SearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * ElasticSearch 全文检索 — search.elasticsearch.enabled=true 时激活。
 * ES 7.17 + IK 中文分词, NativeQuery + MatchQuery。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "true")
public class EsSearchServiceImpl implements SearchService {

    private final ElasticsearchOperations esOps;
    private final RedisTemplate<String, String> redisTemplate;
    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final ElasticsearchClient esClient;

    private static final String HOT_KEYWORDS_KEY = "search:hot:keywords";
    private static final String HISTORY_KEY_PREFIX = "search:history:";

    @Override
    public Page<ProductSearchVO> search(String keyword, Long categoryId, int page, int size) {
        if (keyword != null && !keyword.isBlank()) {
            recordKeyword(keyword.trim());
        }

        var bool = new BoolQuery.Builder();

        if (keyword != null && !keyword.isBlank()) {
            bool.should(Query.of(q -> q.match(MatchQuery.of(m -> m.field("name").query(keyword)))));
            bool.should(Query.of(q -> q.match(MatchQuery.of(m -> m.field("description").query(keyword)))));
            bool.minimumShouldMatch("1");
        } else {
            bool.must(Query.of(q -> q.matchAll(m -> m)));
        }

        if (categoryId != null) {
            bool.filter(Query.of(q -> q.term(t -> t.field("categoryId").value(categoryId))));
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(Query.of(q -> q.bool(bool.build())))
                .withPageable(PageRequest.of(page - 1, size))
                .build();

        SearchHits<ProductSearchVO> hits = esOps.search(query, ProductSearchVO.class);

        List<ProductSearchVO> vos = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .toList();

        Page<ProductSearchVO> voPage = new Page<>(page, size, hits.getTotalHits());
        voPage.setRecords(vos);
        return voPage;
    }

    @Override
    public List<String> getHotKeywords(int limit) {
        var top = redisTemplate.opsForZSet().reverseRange(HOT_KEYWORDS_KEY, 0, limit - 1);
        return top != null ? new ArrayList<>(top) : Collections.emptyList();
    }

    @Override
    public List<String> getSearchHistory(Long userId, int limit) {
        String key = HISTORY_KEY_PREFIX + userId;
        List<String> history = redisTemplate.opsForList().range(key, 0, limit - 1);
        return history != null ? history : Collections.emptyList();
    }

    @Override
    public List<ProductSearchVO> vectorSearch(double[] embedding, int size) {
        // Convert double[] to float[] (ES kNN requires float vectors)
        float[] floatEmbedding = new float[embedding.length];
        for (int i = 0; i < embedding.length; i++) {
            floatEmbedding[i] = (float) embedding[i];
        }

        Query knn = Query.of(q -> q.knn(KnnQuery.of(k -> k
                .field("embedding")
                .queryVector(floatToFloatList(floatEmbedding))
                .k(size)
                .numCandidates(size * 2))));

        NativeQuery query = NativeQuery.builder()
                .withQuery(knn)
                .withPageable(PageRequest.of(0, size))
                .build();

        SearchHits<ProductSearchVO> hits = esOps.search(query, ProductSearchVO.class);
        return hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .toList();
    }

    private static List<Float> floatToFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float f : array) {
            list.add(f);
        }
        return list;
    }

    @Override
    public Map<String, Object> reindex() {
        List<ProductEntity> products = productMapper.selectList(
                new LambdaQueryWrapper<ProductEntity>().eq(ProductEntity::getStatus, 1));
        Map<Long, Integer> priceMap = new HashMap<>();
        for (ProductEntity p : products) {
            List<SkuEntity> skus = skuMapper.selectList(
                    new LambdaQueryWrapper<SkuEntity>()
                            .eq(SkuEntity::getProductId, p.getId())
                            .eq(SkuEntity::getStatus, 1));
            priceMap.put(p.getId(), skus.stream().mapToInt(SkuEntity::getPrice).min().orElse(0));
        }

        if (esClient != null) {
            try {
                try { esClient.indices().delete(d -> d.index("products")); } catch (Exception ignored) {}
                BulkRequest.Builder bulk = new BulkRequest.Builder();
                for (ProductEntity p : products) {
                    Map<String, Object> doc = new LinkedHashMap<>();
                    doc.put("id", p.getId()); doc.put("productId", p.getProductId());
                    doc.put("name", p.getName()); doc.put("description", p.getDescription());
                    doc.put("brand", p.getBrand()); doc.put("categoryId", p.getCategoryId());
                    doc.put("price", priceMap.getOrDefault(p.getId(), 0));
                    doc.put("mainImage", p.getMainImage()); doc.put("soldCount", p.getSoldCount());
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
        }
        return Map.of("status", "ok", "count", products.size(), "esEnabled", esClient != null);
    }

    private void recordKeyword(String keyword) {
        try {
            redisTemplate.opsForZSet().incrementScore(HOT_KEYWORDS_KEY, keyword, 1);
        } catch (Exception ignored) {
        }
    }
}
