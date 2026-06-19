package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.domain.dto.ProductSearchResult;
import org.icedAmericanoMall.service.SearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ElasticSearch 全文检索实现 — 仅在 search.elasticsearch.enabled=true 时激活。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "true")
public class EsSearchServiceImpl implements SearchService {

    private final ElasticsearchOperations esOps;

    @Override
    public Page<ProductSearchResult> search(String keyword, Long categoryId, int page, int size) {
        Criteria criteria = new Criteria();
        if (keyword != null && !keyword.isBlank()) {
            criteria = criteria.and("name").contains(keyword)
                    .or("description").contains(keyword);
        }
        if (categoryId != null) {
            criteria = criteria.and("categoryId").is(categoryId);
        }

        CriteriaQuery query = new CriteriaQuery(criteria)
                .setPageable(org.springframework.data.domain.PageRequest.of(page - 1, size));

        SearchHits<ProductSearchResult> hits = esOps.search(query, ProductSearchResult.class);

        List<ProductSearchResult> results = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());

        Page<ProductSearchResult> resultPage = new Page<>(page, size);
        resultPage.setRecords(results);
        resultPage.setTotal(hits.getTotalHits());
        return resultPage;
    }

    @Override
    public List<String> getHotKeywords() {
        // ES 聚合查询热门搜索词 — V1.1 先返回固定列表，后续接搜索日志
        return List.of("手机", "电脑", "鞋子", "衣服", "耳机");
    }
}
