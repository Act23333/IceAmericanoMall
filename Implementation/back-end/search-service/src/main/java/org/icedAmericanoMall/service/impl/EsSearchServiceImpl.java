package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.domain.vo.ProductSearchVO;
import org.icedAmericanoMall.service.SearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * ElasticSearch 全文检索 — search.elasticsearch.enabled=true 时激活。
 * ES 7.17 + IK 中文分词。
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

        Criteria criteria = new Criteria();
        if (keyword != null && !keyword.isBlank()) {
            criteria = criteria.and(new Criteria("name").contains(keyword)
                    .or("description").contains(keyword));
        }
        if (categoryId != null) {
            criteria = criteria.and(new Criteria("categoryId").is(categoryId));
        }

        CriteriaQuery query = new CriteriaQuery(criteria)
                .setPageable(org.springframework.data.domain.PageRequest.of(page - 1, size));

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

    private void recordKeyword(String keyword) {
        try {
            redisTemplate.opsForZSet().incrementScore(HOT_KEYWORDS_KEY, keyword, 1);
        } catch (Exception ignored) {
        }
    }
}
