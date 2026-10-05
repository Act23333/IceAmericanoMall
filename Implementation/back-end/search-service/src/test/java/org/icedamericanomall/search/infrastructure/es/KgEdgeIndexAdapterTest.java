package org.icedamericanomall.search.infrastructure.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.util.ObjectBuilder;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.search.domain.model.kg.KgEdgeDoc;
import org.icedamericanomall.search.domain.model.kg.KgNeighborHit;
import org.icedamericanomall.search.domain.model.kg.KgNodeRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KgEdgeIndexAdapter 索引生命周期与读写测试 —— mock ES 客户端（对齐 KnowledgeVectorIndexAdapterTest 先例）。
 *
 * <p>锁死「索引不存在才创建 + 并发建索引无害化 + 写失败 fail-loud + 读失败 fail-open」四条不变量。
 * 与先例一致，Function 重载调用点使用类型化 matcher 消除重载歧义。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KgEdgeIndexAdapterTest {

    @Mock
    private ElasticsearchClient esClient;

    @Mock
    private ElasticsearchIndicesClient indicesClient;

    private KgEdgeIndexAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new KgEdgeIndexAdapter(esClient);
        when(esClient.indices()).thenReturn(indicesClient);
    }

    // ── 索引生命周期 ──

    @Test
    @DisplayName("建索引 — 索引不存在 → 按默认 mapping 创建")
    void shouldCreateIndex_whenMissing() throws Exception {
        BooleanResponse notExists = existsResponse(false);
        when(indicesClient.exists(anyExistsFn())).thenReturn(notExists);
        when(indicesClient.create(anyCreateFn())).thenReturn(mock(CreateIndexResponse.class));

        adapter.ensureIndex();

        verify(indicesClient).create(anyCreateFn());
    }

    @Test
    @DisplayName("建索引 — 索引已存在 → 跳过创建（幂等）")
    void shouldSkipCreate_whenIndexExists() throws Exception {
        BooleanResponse exists = existsResponse(true);
        when(indicesClient.exists(anyExistsFn())).thenReturn(exists);

        adapter.ensureIndex();

        verify(indicesClient, never()).create(anyCreateFn());
    }

    @Test
    @DisplayName("建索引 — 并发已被他人创建（resource_already_exists）→ 静默通过")
    void shouldSwallowAlreadyExists_whenConcurrentCreate() throws Exception {
        BooleanResponse notExists = existsResponse(false);
        when(indicesClient.exists(anyExistsFn())).thenReturn(notExists);
        when(indicesClient.create(anyCreateFn())).thenThrow(new RuntimeException(
                "resource_already_exists_exception: index [kg_edges] already exists"));

        adapter.ensureIndex();

        verify(indicesClient).create(anyCreateFn());
    }

    @Test
    @DisplayName("建索引 — 其它 ES 故障 → 抛 BizException（fail-loud，由调用方降级）")
    void shouldThrowBizException_whenCreateFails() throws Exception {
        BooleanResponse notExists = existsResponse(false);
        when(indicesClient.exists(anyExistsFn())).thenReturn(notExists);
        when(indicesClient.create(anyCreateFn())).thenThrow(new RuntimeException("connection refused"));

        assertThrows(BizException.class, () -> adapter.ensureIndex());
    }

    // ── 按 src 清边 ──

    @Test
    @DisplayName("清边 — deleteByQuery 正常返回 → 不抛异常")
    void shouldDeleteBySrc_whenDeleteByQuerySucceeds() throws Exception {
        adapter.deleteBySrc("product", 100L);

        verify(esClient).deleteByQuery(anyDeleteFn());
    }

    @Test
    @DisplayName("清边 — ES 故障 → 抛 BizException（fail-loud）")
    void shouldThrowBizException_whenDeleteBySrcFails() throws Exception {
        when(esClient.deleteByQuery(anyDeleteFn())).thenThrow(new RuntimeException("connection refused"));

        assertThrows(BizException.class, () -> adapter.deleteBySrc("product", 100L));
    }

    // ── 批量写边 ──

    @Test
    @DisplayName("写边 — bulk 全部成功 → 正常返回")
    void shouldIndexEdges_whenBulkSucceeds() throws Exception {
        BulkResponse ok = bulkResponse(false);
        when(esClient.bulk(any(BulkRequest.class))).thenReturn(ok);

        adapter.indexEdges(List.of(edge("BELONGS_TO", "category", 10L)));

        verify(esClient).bulk(any(BulkRequest.class));
    }

    @Test
    @DisplayName("写边 — bulk 部分失败 → 抛 BizException（fail-loud）")
    void shouldThrowBizException_whenBulkHasErrors() throws Exception {
        BulkResponse withErrors = bulkResponse(true);
        when(esClient.bulk(any(BulkRequest.class))).thenReturn(withErrors);

        assertThrows(BizException.class,
                () -> adapter.indexEdges(List.of(edge("BELONGS_TO", "category", 10L))));
    }

    @Test
    @DisplayName("写边 — 空集合 → 空操作，不发起 bulk")
    void shouldSkipBulk_whenEdgesEmpty() throws Exception {
        adapter.indexEdges(List.of());

        verify(esClient, never()).bulk(any(BulkRequest.class));
    }

    // ── 邻居检索 ──

    @Test
    @DisplayName("邻居检索 — 命中解析为 dst/relType/weight + ES relevance score")
    void shouldMapHits_whenSearchNeighborsSucceeds() throws Exception {
        ObjectNode source = JsonNodeFactory.instance.objectNode();
        source.put("dstType", "category");
        source.put("dstId", 10);
        source.put("relType", "BELONGS_TO");
        source.put("weight", 0);
        when(esClient.search(anySearchFn(), ArgumentMatchers.eq(ObjectNode.class)))
                .thenReturn(searchResponse(source, 1.5));

        List<KgNeighborHit> hits = adapter.searchNeighbors(
                List.of(new KgNodeRef("product", 100L)), null, null, 1, 100);

        assertEquals(1, hits.size());
        assertEquals("category", hits.get(0).dstType());
        assertEquals(10L, hits.get(0).dstId());
        assertEquals("BELONGS_TO", hits.get(0).relType());
        assertEquals(0, hits.get(0).weight());
        assertEquals(1.5F, hits.get(0).score());
    }

    @Test
    @DisplayName("邻居检索 — ES 故障 → 空列表（fail-open，读路径不放大故障）")
    void shouldReturnEmpty_whenSearchFails() throws Exception {
        when(esClient.search(anySearchFn(), ArgumentMatchers.eq(ObjectNode.class)))
                .thenThrow(new RuntimeException("connection refused"));

        List<KgNeighborHit> hits = adapter.searchNeighbors(
                List.of(new KgNodeRef("product", 100L)), null, null, 1, 100);

        assertTrue(hits.isEmpty());
    }

    @Test
    @DisplayName("邻居检索 — 起点集合为空 → 不发起 ES 调用直接返回空")
    void shouldNotCallEs_whenSrcNodesEmpty() throws Exception {
        List<KgNeighborHit> hits = adapter.searchNeighbors(List.of(), null, null, 1, 100);

        assertTrue(hits.isEmpty());
        verify(esClient, never()).search(anySearchFn(), ArgumentMatchers.eq(ObjectNode.class));
    }

    // ── 工具 ──

    private static KgEdgeDoc edge(String relType, String dstType, Long dstId) {
        return new KgEdgeDoc("product", 100L, relType, dstType, dstId, 0, "DIRECT", 1);
    }

    private static BulkResponse bulkResponse(boolean errors) {
        BulkResponse response = mock(BulkResponse.class);
        when(response.errors()).thenReturn(errors);
        when(response.items()).thenReturn(List.of());
        return response;
    }

    private static SearchResponse<ObjectNode> searchResponse(ObjectNode source, double score) {
        return SearchResponse.of(r -> r
                .took(1)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0))
                .hits(h -> h.hits(List.of(
                        Hit.of(hit -> hit.index(KgEdgeMapping.INDEX_NAME)
                                .id("product:100:BELONGS_TO:category:10")
                                .score(score)
                                .source(source))))));
    }

    private static BooleanResponse existsResponse(boolean value) {
        BooleanResponse response = mock(BooleanResponse.class);
        when(response.value()).thenReturn(value);
        return response;
    }

    /** 类型化 matcher：消除 exists(Request) / exists(Function) 的重载歧义。 */
    private static Function<ExistsRequest.Builder, ObjectBuilder<ExistsRequest>> anyExistsFn() {
        return ArgumentMatchers.any();
    }

    /** 类型化 matcher：消除 create(Request) / create(Function) 的重载歧义。 */
    private static Function<CreateIndexRequest.Builder, ObjectBuilder<CreateIndexRequest>> anyCreateFn() {
        return ArgumentMatchers.any();
    }

    /** 类型化 matcher：search(Function, Class) 重载的函数入参。 */
    private static Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>> anySearchFn() {
        return ArgumentMatchers.any();
    }

    /** 类型化 matcher：消除 deleteByQuery(Request) / deleteByQuery(Function) 的重载歧义。 */
    private static Function<DeleteByQueryRequest.Builder, ObjectBuilder<DeleteByQueryRequest>> anyDeleteFn() {
        return ArgumentMatchers.any();
    }
}
