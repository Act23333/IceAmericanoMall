package org.icedamericanomall.search.infrastructure.es;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.RangeBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.json.JsonpSerializable;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import jakarta.json.stream.JsonGenerator;
import org.icedamericanomall.search.domain.model.search.SearchFacets;
import org.icedamericanomall.search.domain.model.search.SearchQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SpuSearchRequestBuilder} 单元测试 —— SE1 过滤/排序装配与 SE6 facet 请求/解析（纯函数）。
 *
 * <p>ES 链 filter 拼装以序列化 JSON 断言 Query 结构（仓内既有 serialize 桩模式，
 * 同 {@code EsSpuSearchGatewayTest}）；sort 分支断言 {@code NativeQuery.getSort()}。
 */
@DisplayName("SpuSearchRequestBuilder — SE1 查询/排序装配 + SE6 facet 请求与解析")
class SpuSearchRequestBuilderTest {

    @Test
    @DisplayName("SE1 — brand 过滤 → term 查询（忽略大小写，与 DB 链 ci 口径一致）")
    void shouldFilterByBrandTerm_whenBrandProvided() {
        Query query = SpuSearchRequestBuilder.buildQuery(
                new SearchQuery(null, null, 1, 10, "SoundMax", null, null, null));

        String json = serialize(query);
        assertTrue(json.contains("\"term\""), "应为 term 过滤");
        assertTrue(json.contains("\"brand\""), "应作用于 brand 字段");
        assertTrue(json.contains("case_insensitive"), "应忽略大小写（与 MySQL ci collation 同口径）");
    }

    @Test
    @DisplayName("SE1 — min/max 价格过滤 → price range 查询（口径为在售 SKU 最低价）")
    void shouldFilterByPriceRange_whenRangeProvided() {
        Query query = SpuSearchRequestBuilder.buildQuery(
                new SearchQuery(null, null, 1, 10, null, 10_000, 50_000, null));

        String json = serialize(query);
        assertTrue(json.contains("\"range\""), "应为 range 过滤");
        assertTrue(json.contains("\"price\""), "应作用于 price 字段");
        assertTrue(json.contains("gte"), "应含下界");
        assertTrue(json.contains("lte"), "应含上界");
    }

    @Test
    @DisplayName("SE1 — 仅 min 价格过滤 → 只含 gte")
    void shouldFilterOnlyByLowerBound_whenOnlyMinPriceProvided() {
        Query query = SpuSearchRequestBuilder.buildQuery(
                new SearchQuery(null, null, 1, 10, null, 10_000, null, null));

        String json = serialize(query);
        assertTrue(json.contains("gte"));
        assertFalse(json.contains("\"lte\""), "未传 maxPrice 不应有上界");
    }

    @Test
    @DisplayName("SE1 — categoryId 过滤 → categoryId term")
    void shouldFilterByCategory_whenCategoryIdProvided() {
        Query query = SpuSearchRequestBuilder.buildQuery(
                new SearchQuery(null, 10L, 1, 10, null, null, null, null));

        assertTrue(serialize(query).contains("\"categoryId\""));
    }

    @Test
    @DisplayName("SE1 — 关键词 → name/description 双 should + minimum_should_match=1")
    void shouldMatchKeywordAcrossNameAndDescription_whenKeywordProvided() {
        Query query = SpuSearchRequestBuilder.buildQuery(
                new SearchQuery("耳机", null, 1, 10, null, null, null, null));

        String json = serialize(query);
        assertTrue(json.contains("should"), "name/description 应为 should 子句");
        assertTrue(json.contains("minimum_should_match"), "应约束至少命中一个字段");
        assertTrue(json.contains("耳机"));
    }

    @Test
    @DisplayName("SE1 — sort=sales → soldCount 降序")
    void shouldSortBySoldCountDesc_whenSortSales() {
        NativeQuery query = SpuSearchRequestBuilder.buildNativeQuery(
                new SearchQuery(null, null, 1, 10, null, null, null, "sales"));

        Sort.Order order = assertSortField(query, "soldCount");
        assertEquals(Sort.Direction.DESC, order.getDirection());
    }

    @Test
    @DisplayName("SE1 — sort=price → price 升序")
    void shouldSortByPriceAsc_whenSortPrice() {
        NativeQuery query = SpuSearchRequestBuilder.buildNativeQuery(
                new SearchQuery(null, null, 1, 10, null, null, null, "price"));

        Sort.Order order = assertSortField(query, "price");
        assertEquals(Sort.Direction.ASC, order.getDirection());
    }

    @Test
    @DisplayName("SE1 — sort=newest → publishTime 降序")
    void shouldSortByPublishTimeDesc_whenSortNewest() {
        NativeQuery query = SpuSearchRequestBuilder.buildNativeQuery(
                new SearchQuery(null, null, 1, 10, null, null, null, "newest"));

        Sort.Order order = assertSortField(query, "publishTime");
        assertEquals(Sort.Direction.DESC, order.getDirection());
    }

    @Test
    @DisplayName("SE1 — sort=relevance/未知/缺省 → 不加排序字段（默认打分即现行为）")
    void shouldKeepRelevanceUnsorted_whenSortRelevanceOrUnknown() {
        for (String sort : new String[]{"relevance", "bogus", null}) {
            NativeQuery query = SpuSearchRequestBuilder.buildNativeQuery(
                    new SearchQuery(null, null, 1, 10, null, null, null, sort));
            assertTrue(query.getSort().isUnsorted(), "sort=" + sort + " 应保持默认打分");
        }
    }

    @Test
    @DisplayName("SE6 — facet 请求：只按 keyword/categoryId 过滤（防御性忽略 brand/price）+ 双聚合 + size 0")
    void shouldBuildFacetsRequestIgnoringBrandAndPriceFilters() {
        SearchRequest request = SpuSearchRequestBuilder.buildFacetsRequest(
                new SearchQuery("耳机", 10L, 1, 20, "SoundMax", 100, 200, "price"));

        String json = serialize(request);
        assertTrue(json.contains("\"brands\""), "应含品牌 terms 聚合");
        assertTrue(json.contains("\"priceRanges\""), "应含价格档位 range 聚合");
        assertTrue(json.contains("\"terms\""), "品牌应为 terms 聚合");
        assertTrue(json.contains("\"ranges\""), "价格应为 range 聚合");
        assertTrue(json.contains("\"size\":0"), "facet 请求不取命中");
        assertTrue(json.contains("耳机"), "keyword 过滤应保留");
        assertTrue(json.contains("\"categoryId\""), "categoryId 过滤应保留");
        assertFalse(json.contains("\"case_insensitive\""), "brand 过滤不应进入 facet 查询（过滤前分布）");
        assertFalse(json.contains("\"gte\""), "price 过滤不应进入 facet 查询（过滤前分布）");
        assertFalse(json.contains("\"lte\""), "price 过滤不应进入 facet 查询（过滤前分布）");
    }

    @Test
    @DisplayName("SE6 — 聚合解析：terms/range bucket → 品牌/价格档位 facet")
    void shouldParseBrandAndPriceBuckets_whenAggregatesPresent() {
        Aggregate brands = Aggregate.of(a -> a.sterms(t -> t.buckets(b -> b.array(List.of(
                StringTermsBucket.of(bb -> bb.key("SoundMax").docCount(3)),
                StringTermsBucket.of(bb -> bb.key("AudioPro").docCount(1)))))));
        Aggregate priceRanges = Aggregate.of(a -> a.range(r -> r.buckets(b -> b.array(List.of(
                RangeBucket.of(bb -> bb.from(0.0).to(5000.0).docCount(2)),
                RangeBucket.of(bb -> bb.from(50_000.0).docCount(1)))))));

        SearchFacets facets = SpuSearchRequestBuilder.parseFacets(
                Map.of("brands", brands, "priceRanges", priceRanges));

        assertEquals(2, facets.brands().size());
        assertEquals("SoundMax", facets.brands().get(0).name());
        assertEquals(3, facets.brands().get(0).count());
        assertEquals(2, facets.priceRanges().size());
        assertEquals(0, facets.priceRanges().get(0).min());
        assertEquals(5_000, facets.priceRanges().get(0).max());
        assertEquals(2, facets.priceRanges().get(0).count());
        assertEquals(50_000, facets.priceRanges().get(1).min());
        assertEquals(null, facets.priceRanges().get(1).max(), "末档无上界");
        assertEquals(1, facets.priceRanges().get(1).count());
    }

    @Test
    @DisplayName("SE6 — 聚合缺失 → 空 facet（防御）")
    void shouldReturnEmptyFacets_whenAggregationsMissing() {
        SearchFacets facets = SpuSearchRequestBuilder.parseFacets(Map.of());

        assertTrue(facets.brands().isEmpty());
        assertTrue(facets.priceRanges().isEmpty());
    }

    @Test
    @DisplayName("SE6 — 聚合类型不符 → 该桶为空（防御，不抛异常）")
    void shouldReturnEmptyFacets_whenAggregateKindUnexpected() {
        Aggregate rangeUnderBrandKey = Aggregate.of(a -> a.range(r -> r.buckets(b -> b.array(
                List.of(RangeBucket.of(bb -> bb.from(0.0).to(5000.0).docCount(2)))))));

        SearchFacets facets = SpuSearchRequestBuilder.parseFacets(
                Map.of("brands", rangeUnderBrandKey));

        assertTrue(facets.brands().isEmpty(), "非 terms 聚合按空桶处理");
        assertTrue(facets.priceRanges().isEmpty());
    }

    private Sort.Order assertSortField(NativeQuery query, String field) {
        Sort sort = query.getSort();
        Sort.Order order = sort.getOrderFor(field);
        assertNotNull(order, "应含 " + field + " 排序");
        return order;
    }

    /** JsonpSerializable → JSON（仓内既有 serialize 桩模式）。 */
    private static String serialize(JsonpSerializable value) {
        JacksonJsonpMapper mapper = new JacksonJsonpMapper();
        StringWriter writer = new StringWriter();
        JsonGenerator generator = mapper.jsonProvider().createGenerator(writer);
        value.serialize(generator, mapper);
        generator.close();
        return writer.toString();
    }
}
