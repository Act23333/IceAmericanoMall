package org.icedAmericanoMall.service.impl;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.domain.vo.ProductSearchVO;
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

    private void recordKeyword(String keyword) {
        try {
            redisTemplate.opsForZSet().incrementScore(HOT_KEYWORDS_KEY, keyword, 1);
        } catch (Exception ignored) {
        }
    }
}
