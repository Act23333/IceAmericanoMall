package org.icedamericanomall.search.infrastructure.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.RangeBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.json.JsonpSerializable;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import org.icedamericanomall.client.KnowledgeVectorClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.search.domain.gateway.EmbeddingGateway;
import org.icedamericanomall.search.domain.model.search.ProductVectorDoc;
import org.icedamericanomall.search.domain.model.search.SearchFacets;
import org.icedamericanomall.search.domain.model.search.SearchQuery;
import org.icedamericanomall.search.infrastructure.config.EmbeddingProperties;
import org.icedamericanomall.search.infrastructure.persistence.ProductMapper;
import org.icedamericanomall.search.infrastructure.persistence.ProductPO;
import org.icedamericanomall.search.infrastructure.persistence.SkuMapper;
import org.icedamericanomall.search.infrastructure.persistence.SkuPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EsProductSearchGateway 测试 —— reindex 全量兜底路径的向量能力（ADR-046 §5 修复后口径）
 * + F8 SE1 检索过滤/排序透传与 SE6 facet 降级（05 规格 §4.14.10）。
 *
 * <p>锁死不变量：①索引按 kNN mapping（knn 开关 + dense_vector，dims 同源配置）重建；
 * ②Embedding 已配置 → 逐商品生成向量写入；③单商品向量化失败 → 降级为无向量文档且不中断整批；
 * ④Embedding 未配置 → 向量生成整体跳过；⑤SE1 brand/price 过滤与排序透传 ES；
 * ⑥SE6 facet 聚合成功解析 / 异常降级空对象（不阻断 C 端）。
 * matcher 需显式类型化以消除 Request / Function 重载歧义（同 {@link KnowledgeVectorIndexAdapterTest}）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EsProductSearchGatewayTest {

    private static final String INDEX = KnowledgeVectorClient.INDEX_PRODUCTS;
    private static final int DIMS = 768;

    @Mock
    private ElasticsearchOperations esOps;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private SkuMapper skuMapper;

    @Mock
    private ElasticsearchClient esClient;

    @Mock
    private ElasticsearchIndicesClient indicesClient;

    @Mock
    private EmbeddingGateway embeddingGateway;

    private EsProductSearchGateway gateway;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        EmbeddingProperties embeddingProperties = new EmbeddingProperties();
        embeddingProperties.setDims(DIMS);
        gateway = new EsProductSearchGateway(esOps, productMapper, skuMapper, esClient,
                embeddingProperties, embeddingGateway);

        when(esClient.indices()).thenReturn(indicesClient);
        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.items()).thenReturn(List.of());
        // reindex 以构建好的 BulkRequest 调用（非 Function 重载），matcher 须对应
        when(esClient.bulk(ArgumentMatchers.any(BulkRequest.class))).thenReturn(bulkResponse);
        when(indicesClient.create(anyCreateFn())).thenReturn(mock(CreateIndexResponse.class));
        when(indicesClient.exists(anyExistsFn())).thenReturn(new BooleanResponse(false));
    }

    private ProductPO product(long id) {
        ProductPO po = new ProductPO();
        po.setId(id);
        po.setProductId("biz-" + id);
        po.setName("无线蓝牙耳机" + id);
        po.setDescription("主动降噪 头戴式");
        po.setBrand("SoundMax");
        po.setCategoryId(1L);
        po.setMainImage("img");
        po.setSoldCount(3);
        po.setStatus(1);
        return po;
    }

    private SkuPO sku(long productId, int price) {
        SkuPO sku = new SkuPO();
        sku.setProductId(productId);
        sku.setPrice(price);
        sku.setStatus(1);
        return sku;
    }

    @Test
    @DisplayName("reindex — 按 kNN mapping 重建索引（knn 开关 + dense_vector，dims 同源配置）")
    void shouldRebuildIndexWithKnnMapping_whenReindex() throws Exception {
        when(productMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(product(1L)));
        when(embeddingGateway.isConfigured()).thenReturn(false);

        gateway.reindex();

        verify(indicesClient).delete(anyDeleteFn());
        ArgumentCaptor<Function<CreateIndexRequest.Builder, ObjectBuilder<CreateIndexRequest>>> captor =
                createFnCaptor();
        verify(indicesClient).create(captor.capture());
        CreateIndexRequest request = captor.getValue().apply(new CreateIndexRequest.Builder()).build();
        assertEquals(INDEX, request.index(), "索引名应为 products");
        String json = serialize(request);
        assertTrue(json.contains("knn"), "settings 应含 index.knn 开关");
        assertTrue(json.contains("dense_vector"), "embedding 字段应为 dense_vector");
        assertTrue(json.contains("\"dims\":" + DIMS), "维度应来自 Embedding 配置");
        assertTrue(json.contains("cosine"), "相似度应为 cosine");
        assertTrue(json.contains("\"embedding\""), "向量字段名应为 embedding");
    }

    @Test
    @DisplayName("reindex — Embedding 已配置 → 逐商品生成向量写入（_id=商品 id）")
    void shouldEmbedEachProductAndWriteVector_whenEmbeddingConfigured() throws Exception {
        when(productMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(product(1L), product(2L)));
        when(skuMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(sku(1L, 100)));
        when(embeddingGateway.isConfigured()).thenReturn(true);
        when(embeddingGateway.embed(ArgumentMatchers.any())).thenReturn(new float[]{0.1f, 0.2f});

        Map<String, Object> result = gateway.reindex();

        assertEquals("ok", result.get("status"));
        assertEquals(2, result.get("count"));
        assertEquals(true, result.get("vectorEnabled"));
        assertEquals(0, result.get("vectorFailures"));
        verify(embeddingGateway, times(2)).embed(ArgumentMatchers.any());
        BulkRequest bulkRequest = capturedBulkRequest();
        assertEquals(2, bulkRequest.operations().size());
        assertEquals("1", bulkRequest.operations().get(0).index().id());
        assertEquals(INDEX, bulkRequest.operations().get(0).index().index());
        assertEquals("2", bulkRequest.operations().get(1).index().id());
    }

    @Test
    @DisplayName("reindex — 单商品 Embedding 失败 → 该商品降级无向量文档且不中断整批")
    void shouldContinueWithoutVector_whenSingleProductEmbedFails() throws Exception {
        when(productMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(product(1L), product(2L)));
        when(embeddingGateway.isConfigured()).thenReturn(true);
        when(embeddingGateway.embed(ArgumentMatchers.any()))
                .thenThrow(new BizException(ErrorCode.THIRD_PARTY_CONNECT_FAILED, "embedding down"))
                .thenReturn(new float[]{0.3f});

        Map<String, Object> result = gateway.reindex();

        assertEquals("ok", result.get("status"));
        assertEquals(1, result.get("vectorFailures"));
        assertEquals(2, result.get("count"));
        verify(embeddingGateway, times(2)).embed(ArgumentMatchers.any());
        BulkRequest bulkRequest = capturedBulkRequest();
        assertEquals(2, bulkRequest.operations().size(), "失败商品仍应写入（无向量文档）");
    }

    @Test
    @DisplayName("reindex — Embedding 未配置 → 向量生成整体跳过（无向量重建，不报错）")
    void shouldSkipVectorGeneration_whenEmbeddingNotConfigured() throws Exception {
        when(productMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(product(1L)));
        when(embeddingGateway.isConfigured()).thenReturn(false);

        Map<String, Object> result = gateway.reindex();

        assertEquals("ok", result.get("status"));
        assertEquals(false, result.get("vectorEnabled"));
        assertEquals(0, result.get("vectorFailures"));
        verify(embeddingGateway, never()).embed(ArgumentMatchers.any());
        verify(indicesClient).create(anyCreateFn());
        verify(esClient).bulk(ArgumentMatchers.any(BulkRequest.class));
    }

    @Test
    @DisplayName("docMap — 有向量 → 带 embedding 字段（关键词/展示字段照常）")
    void shouldCarryEmbedding_whenPresent() {
        Map<String, Object> doc = ProductEsDocuments.toDocMap(product(1L), Map.of(1L, 100),
                new float[]{0.1f, 0.2f});

        assertEquals("biz-1", doc.get("productId"));
        assertEquals(100, doc.get("price"));
        assertEquals(List.of(0.1f, 0.2f), doc.get("embedding"));
    }

    @Test
    @DisplayName("docMap — 无向量（降级/未配置）→ 不落 embedding 字段")
    void shouldOmitEmbedding_whenAbsent() {
        Map<String, Object> doc = ProductEsDocuments.toDocMap(product(1L), Map.of(), null);

        assertFalse(doc.containsKey("embedding"));
        assertEquals("无线蓝牙耳机1", doc.get("name"));
    }

    @Test
    @DisplayName("docMap — publishTime → epoch millis 写入（SE1 newest 排序可比）")
    void shouldWritePublishTime_whenPresent() {
        ProductPO po = product(1L);
        po.setPublishTime(LocalDateTime.of(2026, 1, 1, 10, 0));

        Map<String, Object> doc = ProductEsDocuments.toDocMap(po, Map.of(1L, 100), null);

        assertEquals(ProductEsDocuments.toEpochMilli(po.getPublishTime()), doc.get("publishTime"));
    }

    @Test
    @DisplayName("docMap — publishTime 缺失 → 不落字段（newest 排序自然靠后）")
    void shouldOmitPublishTime_whenAbsent() {
        Map<String, Object> doc = ProductEsDocuments.toDocMap(product(1L), Map.of(), null);

        assertFalse(doc.containsKey("publishTime"));
    }

    @Test
    @DisplayName("search — SE1 brand/price 过滤与 price 排序透传 ES")
    void shouldApplyBrandAndPriceFilterAndSort_whenSearch() {
        SearchHits<ProductDocument> hits = searchHits(List.of());
        when(esOps.search(ArgumentMatchers.any(NativeQuery.class), ArgumentMatchers.eq(ProductDocument.class)))
                .thenReturn(hits);

        gateway.search(new SearchQuery("耳机", null, 1, 10, "SoundMax", 10_000, 50_000, "price"));

        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(esOps).search(captor.capture(), ArgumentMatchers.eq(ProductDocument.class));
        NativeQuery captured = captor.getValue();
        String json = serialize(captured.getQuery());
        assertTrue(json.contains("case_insensitive"), "brand 过滤应透传（term 忽略大小写）");
        assertTrue(json.contains("gte") && json.contains("lte"), "price 区间过滤应透传");
        assertEquals(Sort.Direction.ASC, captured.getSort().getOrderFor("price").getDirection(),
                "sort=price 应为 price 升序");
    }

    @Test
    @DisplayName("facets — 聚合成功 → 解析为品牌/价格档位 facet（请求不含 brand/price 过滤）")
    void shouldReturnParsedFacets_whenAggregationSucceeds() throws Exception {
        Aggregate brands = Aggregate.of(a -> a.sterms(t -> t.buckets(b -> b.array(List.of(
                StringTermsBucket.of(bb -> bb.key("SoundMax").docCount(3)))))));
        Aggregate priceRanges = Aggregate.of(a -> a.range(r -> r.buckets(b -> b.array(List.of(
                RangeBucket.of(bb -> bb.from(0.0).to(5000.0).docCount(2)))))));
        when(esClient.search(ArgumentMatchers.any(SearchRequest.class),
                ArgumentMatchers.eq(ProductDocument.class)))
                .thenReturn(facetsResponse(Map.of("brands", brands, "priceRanges", priceRanges)));

        SearchFacets facets = gateway.facets(new SearchQuery("耳机", null, 1, 10));

        assertEquals(1, facets.brands().size());
        assertEquals("SoundMax", facets.brands().get(0).name());
        assertEquals(3, facets.brands().get(0).count());
        assertEquals(1, facets.priceRanges().size());
        assertEquals(0, facets.priceRanges().get(0).min());
        assertEquals(5_000, facets.priceRanges().get(0).max());
        assertEquals(2, facets.priceRanges().get(0).count());

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(esClient).search(requestCaptor.capture(), ArgumentMatchers.eq(ProductDocument.class));
        String requestJson = serialize(requestCaptor.getValue());
        assertTrue(requestJson.contains("\"size\":0"), "facet 请求不取命中");
        assertFalse(requestJson.contains("case_insensitive"), "facet 反映过滤前分布，brand 过滤不进聚合查询");
        assertFalse(requestJson.contains("gte"), "facet 反映过滤前分布，price 过滤不进聚合查询");
    }

    @Test
    @DisplayName("facets — 聚合异常 → 空 facet 降级（辅助 UI 不阻断 C 端搜索）")
    void shouldReturnEmptyFacets_whenAggregationFails() throws Exception {
        when(esClient.search(ArgumentMatchers.any(SearchRequest.class),
                ArgumentMatchers.eq(ProductDocument.class)))
                .thenThrow(new RuntimeException("es down"));

        SearchFacets facets = gateway.facets(new SearchQuery("耳机", null, 1, 10));

        assertTrue(facets.brands().isEmpty());
        assertTrue(facets.priceRanges().isEmpty());
    }

    @Test
    @DisplayName("upsertVectorDoc — I47 覆盖写回填 publishTime（契约入参无此字段，从镜像表补齐）")
    void shouldEnrichPublishTime_whenUpsertVectorDoc() {
        ProductPO po = product(1L);
        po.setPublishTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        when(productMapper.selectById(1L)).thenReturn(po);

        gateway.upsertVectorDoc(vectorDoc(1L));

        ArgumentCaptor<ProductDocument> captor = ArgumentCaptor.forClass(ProductDocument.class);
        verify(esOps).save(captor.capture());
        assertEquals(ProductEsDocuments.toEpochMilli(po.getPublishTime()), captor.getValue().getPublishTime());
    }

    @Test
    @DisplayName("upsertVectorDoc — 镜像表无该商品/无发布时间 → 不回填不报错（fail-open）")
    void shouldSkipPublishTimeEnrichment_whenProductMissing() {
        when(productMapper.selectById(1L)).thenReturn(null);

        gateway.upsertVectorDoc(vectorDoc(1L));

        ArgumentCaptor<ProductDocument> captor = ArgumentCaptor.forClass(ProductDocument.class);
        verify(esOps).save(captor.capture());
        assertNull(captor.getValue().getPublishTime());
    }

    private ProductVectorDoc vectorDoc(long productId) {
        return new ProductVectorDoc(productId, "无线蓝牙耳机", "主动降噪", "SoundMax", 1L, "img",
                100, 3, new double[]{0.1, 0.2});
    }

    @SuppressWarnings("unchecked")
    private SearchHits<ProductDocument> searchHits(List<SearchHit<ProductDocument>> hits) {
        SearchHits<ProductDocument> mockHits = mock(SearchHits.class);
        when(mockHits.getSearchHits()).thenReturn(hits);
        when(mockHits.getTotalHits()).thenReturn((long) hits.size());
        return mockHits;
    }

    private static SearchResponse<ProductDocument> facetsResponse(Map<String, Aggregate> aggregations) {
        return SearchResponse.of(resp -> resp
                .took(1)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).skipped(0).failed(0))
                .hits(h -> h.total(t -> t.value(0).relation(TotalHitsRelation.Eq)).hits(List.of()))
                .aggregations(aggregations));
    }

    private BulkRequest capturedBulkRequest() throws Exception {
        ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(esClient).bulk(captor.capture());
        return captor.getValue();
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

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Function<CreateIndexRequest.Builder, ObjectBuilder<CreateIndexRequest>>>
    createFnCaptor() {
        return ArgumentCaptor.forClass((Class) Function.class);
    }

    /** 类型化 matcher：消除 delete(Request) / delete(Function) 的重载歧义。 */
    private static Function<co.elastic.clients.elasticsearch.indices.DeleteIndexRequest.Builder,
            ObjectBuilder<co.elastic.clients.elasticsearch.indices.DeleteIndexRequest>> anyDeleteFn() {
        return ArgumentMatchers.any();
    }

    /** 类型化 matcher：消除 create(Request) / create(Function) 的重载歧义。 */
    private static Function<CreateIndexRequest.Builder, ObjectBuilder<CreateIndexRequest>> anyCreateFn() {
        return ArgumentMatchers.any();
    }

    /** 类型化 matcher：消除 exists(Request) / exists(Function) 的重载歧义。 */
    private static Function<ExistsRequest.Builder, ObjectBuilder<ExistsRequest>> anyExistsFn() {
        return ArgumentMatchers.any();
    }
}
