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
import org.icedamericanomall.client.ItemSearchClient;
import org.icedamericanomall.client.KnowledgeVectorClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.SpuSearchItemCO;
import org.icedamericanomall.dto.SpuSearchPageCO;
import org.icedamericanomall.search.domain.gateway.EmbeddingGateway;
import org.icedamericanomall.search.domain.model.search.SpuVectorDoc;
import org.icedamericanomall.search.domain.model.search.SearchFacets;
import org.icedamericanomall.search.domain.model.search.SearchQuery;
import org.icedamericanomall.search.infrastructure.config.EmbeddingProperties;
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
 * EsSpuSearchGateway 测试 —— reindex 全量兜底路径的向量能力（ADR-046 §5 修复后口径）
 * + F8 SE1 检索过滤/排序透传与 SE6 facet 降级（05 规格 §4.14.10）。
 *
 * <p><b>数据面（ADR-052）</b>：reindex 商品数据与 publishTime 回填改为 mock
 * {@link ItemSearchClient}（item 域检索端点），取代原镜像表 Mapper mock；
 * 分页循环拉取（页长 500）为新增覆盖。
 *
 * <p>锁死不变量：①索引按 kNN mapping（knn 开关 + dense_vector，dims 同源配置）重建；
 * ②Embedding 已配置 → 逐商品生成向量写入；③单商品向量化失败 → 降级为无向量文档且不中断整批；
 * ④Embedding 未配置 → 向量生成整体跳过；⑤SE1 brand/price 过滤与排序透传 ES；
 * ⑥SE6 facet 聚合成功解析 / 异常降级空对象（不阻断 C 端）；⑦reindex 分页拉取直至耗尽。
 * matcher 需显式类型化以消除 Request / Function 重载歧义（同 {@link KnowledgeVectorIndexAdapterTest}）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EsSpuSearchGatewayTest {

    private static final String INDEX = KnowledgeVectorClient.INDEX_PRODUCTS;
    private static final int DIMS = 768;

    /** SPU <b>业务键</b>（32-hex，ADR-061 §决策 2：I47 幂等键 / ES 文档 spuId 列）。 */
    private static final String SPU_BUSINESS_ID = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";

    @Mock
    private ElasticsearchOperations esOps;

    @Mock
    private ElasticsearchClient esClient;

    @Mock
    private ElasticsearchIndicesClient indicesClient;

    @Mock
    private EmbeddingGateway embeddingGateway;

    @Mock
    private ItemSearchClient itemSearchClient;

    private EsSpuSearchGateway gateway;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        EmbeddingProperties embeddingProperties = new EmbeddingProperties();
        embeddingProperties.setDims(DIMS);
        gateway = new EsSpuSearchGateway(esOps, esClient, embeddingProperties, embeddingGateway,
                itemSearchClient);

        when(esClient.indices()).thenReturn(indicesClient);
        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.items()).thenReturn(List.of());
        // reindex 以构建好的 BulkRequest 调用（非 Function 重载），matcher 须对应
        when(esClient.bulk(ArgumentMatchers.any(BulkRequest.class))).thenReturn(bulkResponse);
        when(indicesClient.create(anyCreateFn())).thenReturn(mock(CreateIndexResponse.class));
        when(indicesClient.exists(anyExistsFn())).thenReturn(new BooleanResponse(false));
    }

    /** item 域检索卡片（ADR-052 数据面形态：spuId 已 hex、minPrice/publishTime 已归一）。 */
    private SpuSearchItemCO item(long id) {
        SpuSearchItemCO co = new SpuSearchItemCO();
        co.setId(id);
        // 32-hex 业务键（技术键参与构值，保证「不同技术键 → 不同业务键」；
        // 长度必须是 32 —— 少于/多于都不是合法 BINARY(16) 的 hex 形态，ADR-050）
        co.setSpuId(String.format("%032x", 0xB0000000L + id));
        co.setSpuName("无线蓝牙耳机" + id);
        co.setDescription("主动降噪 头戴式");
        co.setBrand("SoundMax");
        co.setCategoryId(1L);
        co.setMainImage("img");
        co.setSoldCount(3);
        co.setMinPrice(100);
        return co;
    }

    private static SpuSearchPageCO page(List<SpuSearchItemCO> records, long total) {
        SpuSearchPageCO co = new SpuSearchPageCO();
        co.setRecords(records);
        co.setTotal(total);
        return co;
    }

    private void stubExport(SpuSearchPageCO... pages) {
        var stub = when(itemSearchClient.exportPage(ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt()));
        for (SpuSearchPageCO p : pages) {
            stub = stub.thenReturn(p);
        }
    }

    @Test
    @DisplayName("reindex — 按 kNN mapping 重建索引（knn 开关 + dense_vector，dims 同源配置）")
    void shouldRebuildIndexWithKnnMapping_whenReindex() throws Exception {
        stubExport(page(List.of(item(1L)), 1));
        when(embeddingGateway.isConfigured()).thenReturn(false);

        gateway.reindex();

        verify(indicesClient).delete(anyDeleteFn());
        ArgumentCaptor<Function<CreateIndexRequest.Builder, ObjectBuilder<CreateIndexRequest>>> captor =
                createFnCaptor();
        verify(indicesClient).create(captor.capture());
        CreateIndexRequest request = captor.getValue().apply(new CreateIndexRequest.Builder()).build();
        assertEquals(INDEX, request.index(), "索引名应为 spus");
        String json = serialize(request);
        assertTrue(json.contains("knn"), "settings 应含 index.knn 开关");
        assertTrue(json.contains("dense_vector"), "embedding 字段应为 dense_vector");
        assertTrue(json.contains("\"dims\":" + DIMS), "维度应来自 Embedding 配置");
        assertTrue(json.contains("cosine"), "相似度应为 cosine");
        assertTrue(json.contains("\"embedding\""), "向量字段名应为 embedding");
    }

    @Test
    @DisplayName("reindex — Embedding 已配置 → 逐商品生成向量写入（_id=商品 id）")
    void shouldEmbedEachSpuAndWriteVector_whenEmbeddingConfigured() throws Exception {
        stubExport(page(List.of(item(1L), item(2L)), 2));
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
    @DisplayName("reindex — 分页循环拉取：跨页累计直至耗尽（ADR-052 新增覆盖）")
    void shouldFetchAllPages_whenReindex() throws Exception {
        stubExport(page(List.of(item(1L), item(2L)), 3),
                page(List.of(item(3L)), 3));
        when(embeddingGateway.isConfigured()).thenReturn(false);

        Map<String, Object> result = gateway.reindex();

        assertEquals(3, result.get("count"));
        verify(itemSearchClient, times(2)).exportPage(ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt());
        assertEquals(3, capturedBulkRequest().operations().size());
    }

    @Test
    @DisplayName("reindex — 单商品 Embedding 失败 → 该商品降级无向量文档且不中断整批")
    void shouldContinueWithoutVector_whenSingleSpuEmbedFails() throws Exception {
        stubExport(page(List.of(item(1L), item(2L)), 2));
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
        stubExport(page(List.of(item(1L)), 1));
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
        Map<String, Object> doc = SpuEsDocuments.toDocMap(item(1L), new float[]{0.1f, 0.2f});

        // spuId 是 item 域带出的 32-hex 业务键（非技术键；技术键在 id 列）
        assertEquals(String.format("%032x", 0xB0000000L + 1L), doc.get("spuId"));
        assertEquals(1L, doc.get("id"));
        assertEquals(100, doc.get("price"));
        assertEquals(List.of(0.1f, 0.2f), doc.get("embedding"));
    }

    @Test
    @DisplayName("docMap — 无向量（降级/未配置）→ 不落 embedding 字段")
    void shouldOmitEmbedding_whenAbsent() {
        Map<String, Object> doc = SpuEsDocuments.toDocMap(item(1L), null);

        assertFalse(doc.containsKey("embedding"));
        // ES 索引字段名 `name` **有意不变**（契约 §1.8④「不改清单」：索引字段不是报文键，
        // 改名会孤立既有索引与 reindex 产物、收益为零）。收口的只是**VO 映射**：
        // SpuSearchItemCO.spuName → 本 doc map 的 `name` 键（见 SpuEsDocuments.toDocMap）。
        assertEquals("无线蓝牙耳机1", doc.get("name"));
    }

    @Test
    @DisplayName("docMap — publishTime → epoch millis 写入（item 出口已归一，SE1 newest 可比）")
    void shouldWritePublishTime_whenPresent() {
        SpuSearchItemCO co = item(1L);
        co.setPublishTime(1_767_225_600_000L);

        Map<String, Object> doc = SpuEsDocuments.toDocMap(co, null);

        assertEquals(1_767_225_600_000L, doc.get("publishTime"));
    }

    @Test
    @DisplayName("docMap — publishTime 缺失 → 不落字段（newest 排序自然靠后）")
    void shouldOmitPublishTime_whenAbsent() {
        Map<String, Object> doc = SpuEsDocuments.toDocMap(item(1L), null);

        assertFalse(doc.containsKey("publishTime"));
    }

    @Test
    @DisplayName("search — SE1 brand/price 过滤与 price 排序透传 ES")
    void shouldApplyBrandAndPriceFilterAndSort_whenSearch() {
        SearchHits<SpuDocument> hits = searchHits(List.of());
        when(esOps.search(ArgumentMatchers.any(NativeQuery.class), ArgumentMatchers.eq(SpuDocument.class)))
                .thenReturn(hits);

        gateway.search(new SearchQuery("耳机", null, 1, 10, "SoundMax", 10_000, 50_000, "price"));

        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(esOps).search(captor.capture(), ArgumentMatchers.eq(SpuDocument.class));
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
                ArgumentMatchers.eq(SpuDocument.class)))
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
        verify(esClient).search(requestCaptor.capture(), ArgumentMatchers.eq(SpuDocument.class));
        String requestJson = serialize(requestCaptor.getValue());
        assertTrue(requestJson.contains("\"size\":0"), "facet 请求不取命中");
        assertFalse(requestJson.contains("case_insensitive"), "facet 反映过滤前分布，brand 过滤不进聚合查询");
        assertFalse(requestJson.contains("gte"), "facet 反映过滤前分布，price 过滤不进聚合查询");
    }

    @Test
    @DisplayName("facets — 聚合异常 → 空 facet 降级（辅助 UI 不阻断 C 端搜索）")
    void shouldReturnEmptyFacets_whenAggregationFails() throws Exception {
        when(esClient.search(ArgumentMatchers.any(SearchRequest.class),
                ArgumentMatchers.eq(SpuDocument.class)))
                .thenThrow(new RuntimeException("es down"));

        SearchFacets facets = gateway.facets(new SearchQuery("耳机", null, 1, 10));

        assertTrue(facets.brands().isEmpty());
        assertTrue(facets.priceRanges().isEmpty());
    }

    @Test
    @DisplayName("upsertVectorDoc — I47 覆盖写按业务键解析技术键并回填 publishTime（同一次数据面往返）")
    void shouldEnrichPublishTime_whenUpsertVectorDoc() {
        SpuSearchItemCO co = item(1L);
        co.setPublishTime(1_767_225_600_000L);
        // 入参是 SPU 业务键（ADR-061），技术键经 item 域检索出口解析
        when(itemSearchClient.detail(SPU_BUSINESS_ID)).thenReturn(co);

        gateway.upsertVectorDoc(vectorDoc(1L));

        ArgumentCaptor<SpuDocument> captor = ArgumentCaptor.forClass(SpuDocument.class);
        verify(esOps).save(captor.capture());
        SpuDocument saved = captor.getValue();
        assertEquals(1_767_225_600_000L, saved.getPublishTime());
        // 文档键：id 技术键 / spuId 业务键（两者刻意不同值，退回任一即失败）
        assertEquals(1L, saved.getId());
        assertEquals(SPU_BUSINESS_ID, saved.getSpuId());
    }

    @Test
    @DisplayName("upsertVectorDoc — item 端点无该商品 → 跳过写入不报错（fail-open，不产出无主文档）")
    void shouldSkipWrite_whenSpuMissing() {
        when(itemSearchClient.detail(SPU_BUSINESS_ID)).thenReturn(null);

        gateway.upsertVectorDoc(vectorDoc(1L));

        verify(esOps, never()).save(ArgumentMatchers.any(SpuDocument.class));
    }

    @Test
    @DisplayName("findVectorById — 业务键 → 技术键解析后按 _id 取自身向量")
    void shouldFindVectorByBusinessKey() {
        when(itemSearchClient.detail(SPU_BUSINESS_ID)).thenReturn(item(1L));
        SpuDocument doc = new SpuDocument();
        doc.setId(1L);
        doc.setEmbedding(new double[]{0.1, 0.2});
        when(esOps.get("1", SpuDocument.class)).thenReturn(doc);

        assertEquals(2, gateway.findVectorById(SPU_BUSINESS_ID).orElseThrow().length);
    }

    @Test
    @DisplayName("findVectorById — 商品不存在 → empty 且不读 ES（fail-open）")
    void shouldReturnEmpty_whenSpuMissingOnFindVector() {
        when(itemSearchClient.detail(SPU_BUSINESS_ID)).thenReturn(null);

        assertTrue(gateway.findVectorById(SPU_BUSINESS_ID).isEmpty());
        verify(esOps, never()).get(ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(SpuDocument.class));
    }

    private SpuVectorDoc vectorDoc(long ignoredTechnicalId) {
        return new SpuVectorDoc(SPU_BUSINESS_ID, "无线蓝牙耳机", "主动降噪", "SoundMax", 1L, "img",
                100, 3, new double[]{0.1, 0.2});
    }

    @SuppressWarnings("unchecked")
    private SearchHits<SpuDocument> searchHits(List<SearchHit<SpuDocument>> hits) {
        SearchHits<SpuDocument> mockHits = mock(SearchHits.class);
        when(mockHits.getSearchHits()).thenReturn(hits);
        when(mockHits.getTotalHits()).thenReturn((long) hits.size());
        return mockHits;
    }

    private static SearchResponse<SpuDocument> facetsResponse(Map<String, Aggregate> aggregations) {
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
