package org.icedamericanomall.search.infrastructure.es;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationRange;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import org.icedamericanomall.client.KnowledgeVectorClient;
import org.icedamericanomall.search.domain.model.search.SearchFacets;
import org.icedamericanomall.search.domain.model.search.SearchQuery;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 商品检索请求构造与 facet 解析（纯函数，便于单测）—— 自 {@code EsSpuSearchGateway} 下沉
 * （F8 SE1 过滤/排序与 SE6 facet 加入后主类逼近 300 行红线，09-constraints 类规模硬约束）。
 *
 * <p>职责：①keyword/category/brand/price 的 bool 查询与排序装配（SE1）；②facet 聚合请求构造、
 * 聚合结果 → 领域 {@link SearchFacets} 解析（SE6，仅按 keyword/categoryId 过滤——facet 反映过滤前分布）。
 * 字段名与 {@link SpuDocument} / {@link SpuVectorMapping} 一致。
 */
final class SpuSearchRequestBuilder {

    /** 品牌 facet terms 聚合 size（契约 SE6：Top 20，按 count 降序）。 */
    static final int BRAND_FACET_SIZE = 20;

    /**
     * 价格档位边界（分）—— 固定五档：0-50/50-100/100-200/200-500/500+ 元（×100 换算为分）。
     * 相邻元素构成一档 [from, to)；末档只给 from（无上界）。
     */
    private static final int[] PRICE_TIER_BOUNDS = {0, 5_000, 10_000, 20_000, 50_000};

    /** 聚合名（facet 请求构造与结果解析的对称键）。 */
    private static final String AGG_BRANDS = "brands";
    private static final String AGG_PRICE_RANGES = "priceRanges";

    /** ES 字段名。 */
    private static final String FIELD_NAME = "name";
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_CATEGORY_ID = "categoryId";
    private static final String FIELD_BRAND = "brand";
    private static final String FIELD_PRICE = "price";
    private static final String FIELD_SOLD_COUNT = "soldCount";
    private static final String FIELD_PUBLISH_TIME = "publishTime";

    /** 关键词最小命中子句数（name/description 二选一即可）。 */
    private static final String MINIMUM_SHOULD_MATCH = "1";

    private SpuSearchRequestBuilder() {
    }

    /** 关键词检索 NativeQuery（SE1：keyword/category/brand/price 过滤 + 排序）。 */
    static NativeQuery buildNativeQuery(SearchQuery query) {
        return NativeQuery.builder()
                .withQuery(buildQuery(query))
                .withPageable(PageRequest.of(query.page() - 1, query.size()))
                .withSort(resolveSort(query))
                .build();
    }

    /**
     * 检索 bool 查询（SE1）：keyword should-match + 类目/品牌/价格区间 filter。
     * 品牌 term 忽略大小写（与 DB 链 MySQL ci collation 口径一致）；
     * 价格口径为在售 SKU 最低价（与 DB 链 MIN(price) 子查询同口径）。
     */
    static Query buildQuery(SearchQuery query) {
        BoolQuery.Builder bool = new BoolQuery.Builder();
        if (query.hasKeyword()) {
            bool.should(Query.of(q -> q.match(MatchQuery.of(m -> m.field(FIELD_NAME).query(query.keyword())))));
            bool.should(Query.of(q -> q.match(MatchQuery.of(m -> m.field(FIELD_DESCRIPTION).query(query.keyword())))));
            bool.minimumShouldMatch(MINIMUM_SHOULD_MATCH);
        } else {
            bool.must(Query.of(q -> q.matchAll(m -> m)));
        }
        if (query.categoryId() != null) {
            bool.filter(Query.of(q -> q.term(t -> t.field(FIELD_CATEGORY_ID).value(query.categoryId()))));
        }
        if (query.hasBrand()) {
            bool.filter(Query.of(q -> q.term(t -> t.field(FIELD_BRAND)
                    .value(query.brand()).caseInsensitive(true))));
        }
        if (query.hasPriceRange()) {
            bool.filter(Query.of(q -> q.range(r -> r.number(n -> {
                n.field(FIELD_PRICE);
                if (query.minPrice() != null) {
                    n.gte(query.minPrice().doubleValue());
                }
                if (query.maxPrice() != null) {
                    n.lte(query.maxPrice().doubleValue());
                }
                return n;
            }))));
        }
        return Query.of(q -> q.bool(bool.build()));
    }

    /**
     * SE1 排序：sales=soldCount 降序 / price=price 升序 / newest=publishTime 降序 /
     * relevance=默认打分（不加排序字段）。
     */
    static Sort resolveSort(SearchQuery query) {
        return switch (query.sortOrDefault()) {
            case SALES -> Sort.by(Sort.Direction.DESC, FIELD_SOLD_COUNT);
            case PRICE -> Sort.by(Sort.Direction.ASC, FIELD_PRICE);
            case NEWEST -> Sort.by(Sort.Direction.DESC, FIELD_PUBLISH_TIME);
            case RELEVANCE -> Sort.unsorted();
        };
    }

    /**
     * facet 聚合请求（SE6）：{@code size 0} 不取命中，只带 keyword/categoryId（防御性忽略
     * brand/price——facet 反映过滤前分布），挂品牌 terms 聚合 + 固定价格档位 range 聚合。
     */
    static SearchRequest buildFacetsRequest(SearchQuery query) {
        SearchQuery facetScope = new SearchQuery(query.keyword(), query.categoryId(), 1, 1);
        return SearchRequest.of(s -> s
                .index(KnowledgeVectorClient.INDEX_PRODUCTS)
                .size(0)
                .query(buildQuery(facetScope))
                .aggregations(AGG_BRANDS, a -> a.terms(t -> t.field(FIELD_BRAND).size(BRAND_FACET_SIZE)))
                .aggregations(AGG_PRICE_RANGES, a -> a.range(r -> r.field(FIELD_PRICE).ranges(priceTiers()))));
    }

    /** 聚合结果 → 领域 facet（SE6）；聚合缺失/类型不符按空桶处理（防御，不抛异常）。 */
    static SearchFacets parseFacets(Map<String, Aggregate> aggregations) {
        return new SearchFacets(parseBrandFacets(aggregations.get(AGG_BRANDS)),
                parsePriceFacets(aggregations.get(AGG_PRICE_RANGES)));
    }

    /** 品牌 terms 聚合 → 品牌 facet 桶。 */
    private static List<SearchFacets.BrandFacet> parseBrandFacets(Aggregate aggregate) {
        if (aggregate == null || !aggregate.isSterms()) {
            return List.of();
        }
        return aggregate.sterms().buckets().array().stream()
                .map(bucket -> new SearchFacets.BrandFacet(bucket.key().stringValue(), bucket.docCount()))
                .toList();
    }

    /** 价格 range 聚合 → 价格档位 facet 桶（无界侧为 null）。 */
    private static List<SearchFacets.PriceRangeFacet> parsePriceFacets(Aggregate aggregate) {
        if (aggregate == null || !aggregate.isRange()) {
            return List.of();
        }
        return aggregate.range().buckets().array().stream()
                .map(bucket -> new SearchFacets.PriceRangeFacet(
                        bucket.from() == null ? null : bucket.from().intValue(),
                        bucket.to() == null ? null : bucket.to().intValue(),
                        bucket.docCount()))
                .toList();
    }

    /** 固定五档 range（[from, to)；末档只给 from 表示无上界）。 */
    private static List<AggregationRange> priceTiers() {
        List<AggregationRange> tiers = new ArrayList<>(PRICE_TIER_BOUNDS.length);
        for (int i = 0; i < PRICE_TIER_BOUNDS.length; i++) {
            final double from = PRICE_TIER_BOUNDS[i];
            final Double to = i < PRICE_TIER_BOUNDS.length - 1 ? (double) PRICE_TIER_BOUNDS[i + 1] : null;
            tiers.add(AggregationRange.of(r -> {
                r.from(from);
                if (to != null) {
                    r.to(to);
                }
                return r;
            }));
        }
        return tiers;
    }
}
