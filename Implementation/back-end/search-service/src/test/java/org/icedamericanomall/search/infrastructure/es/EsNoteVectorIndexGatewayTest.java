package org.icedamericanomall.search.infrastructure.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.json.JsonpSerializable;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import org.icedamericanomall.search.domain.model.search.NoteVectorDoc;
import org.icedamericanomall.search.domain.model.search.NoteVectorHit;
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
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.io.StringWriter;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EsNoteVectorIndexGateway 测试 —— 笔记向量 {@code notes} 索引读写（I50/I51，V5.13）。
 *
 * <p>锁死不变量：①upsert 覆盖写 _id=noteId 且索引缺失时按 kNN mapping 建 notes 索引
 * （knn 开关 + dense_vector，dims 同源配置）；②findVectorById 未摄取/ES 异常 → empty（fail-open）；
 * ③deleteById 软删语义；④kNN 检索请求与命中映射（noteId + score）。
 * matcher 显式类型化消除 Request / Function 重载歧义（同 {@code EsProductSearchGatewayTest}）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EsNoteVectorIndexGatewayTest {

    private static final String INDEX = NoteVectorDocument.INDEX_NAME;
    private static final int DIMS = 768;

    @Mock
    private ElasticsearchOperations esOps;

    @Mock
    private ElasticsearchClient esClient;

    @Mock
    private ElasticsearchIndicesClient indicesClient;

    private EsNoteVectorIndexGateway gateway;

    @BeforeEach
    void setUp() throws Exception {
        EmbeddingProperties embeddingProperties = new EmbeddingProperties();
        embeddingProperties.setDims(DIMS);
        gateway = new EsNoteVectorIndexGateway(esOps, esClient, embeddingProperties);

        when(esClient.indices()).thenReturn(indicesClient);
        when(indicesClient.create(anyCreateFn())).thenReturn(mock(CreateIndexResponse.class));
        // 缺省索引已存在（upsert 主路径不建索引）
        when(indicesClient.exists(anyExistsFn())).thenReturn(new BooleanResponse(true));
    }

    private NoteVectorDoc doc(long noteId) {
        return new NoteVectorDoc(noteId, "冲咖啡入门指南", "手冲水温 90 度", new double[]{0.1, 0.2});
    }

    private NoteVectorDocument noteDoc(long noteId) {
        NoteVectorDocument document = new NoteVectorDocument();
        document.setNoteId(noteId);
        document.setTitle("标题" + noteId);
        document.setEmbedding(new double[]{0.1, 0.2});
        return document;
    }

    @Test
    @DisplayName("upsertVectorDoc — I50 覆盖写：_id=noteId，原文与向量以入参为准（索引已存在不重建）")
    void shouldSaveWholeDocWithoutIndexCreate_whenIndexExists() throws Exception {
        gateway.upsertVectorDoc(doc(9L));

        verify(indicesClient, never()).create(anyCreateFn());
        ArgumentCaptor<NoteVectorDocument> captor = ArgumentCaptor.forClass(NoteVectorDocument.class);
        verify(esOps).save(captor.capture());
        NoteVectorDocument saved = captor.getValue();
        assertEquals(9L, saved.getNoteId());
        assertEquals("冲咖啡入门指南", saved.getTitle());
        assertEquals("手冲水温 90 度", saved.getContent());
        assertArrayEquals(new double[]{0.1, 0.2}, saved.getEmbedding());
    }

    @Test
    @DisplayName("upsertVectorDoc — 索引缺失 → 按 kNN mapping 建 notes 索引（knn 开关 + dense_vector，dims 同源）")
    void shouldCreateIndexWithKnnMapping_whenIndexMissing() throws Exception {
        when(indicesClient.exists(anyExistsFn())).thenReturn(new BooleanResponse(false));

        gateway.upsertVectorDoc(doc(9L));

        ArgumentCaptor<Function<CreateIndexRequest.Builder, ObjectBuilder<CreateIndexRequest>>> captor =
                createFnCaptor();
        verify(indicesClient).create(captor.capture());
        CreateIndexRequest request = captor.getValue().apply(new CreateIndexRequest.Builder()).build();
        assertEquals(INDEX, request.index(), "索引名应为 notes");
        String json = serialize(request);
        assertTrue(json.contains("knn"), "settings 应含 index.knn 开关");
        assertTrue(json.contains("dense_vector"), "embedding 字段应为 dense_vector");
        assertTrue(json.contains("\"dims\":" + DIMS), "维度应来自 Embedding 配置");
        assertTrue(json.contains("cosine"), "相似度应为 cosine");
        assertTrue(json.contains("\"noteId\""), "应含 noteId 字段");
    }

    @Test
    @DisplayName("findVectorById — 已摄取 → 返回向量")
    void shouldReturnVector_whenNoteIngested() {
        when(esOps.get("9", NoteVectorDocument.class)).thenReturn(noteDoc(9L));

        Optional<double[]> vector = gateway.findVectorById(9L);

        assertTrue(vector.isPresent());
        assertArrayEquals(new double[]{0.1, 0.2}, vector.get());
    }

    @Test
    @DisplayName("findVectorById — 未摄取/读取异常 → empty（fail-open，仅影响相似检索空结果）")
    void shouldReturnEmpty_whenNoteMissingOrEsFails() {
        when(esOps.get("8", NoteVectorDocument.class)).thenReturn(null);
        assertTrue(gateway.findVectorById(8L).isEmpty());

        when(esOps.get("7", NoteVectorDocument.class)).thenThrow(new RuntimeException("es down"));
        assertTrue(gateway.findVectorById(7L).isEmpty());
    }

    @Test
    @DisplayName("deleteById — I50 deleted=true 语义 → 删除对应文档（_id=noteId 字符串）")
    void shouldDeleteDocument_whenDeleteById() {
        gateway.deleteById(9L);

        verify(esOps).delete("9", NoteVectorDocument.class);
    }

    @Test
    @DisplayName("similar — kNN 检索（Top-K 透传，field=embedding），命中映射为 noteId + score")
    void shouldMapKnnHitsToNoteVectorHits_whenSimilar() {
        // SearchHits 须在 when() 之前构建（嵌套桩触发 UnfinishedStubbing）
        SearchHits<NoteVectorDocument> stubHits =
                searchHits(List.of(searchHit(100L, 0.9f), searchHit(200L, 0.8f)));
        when(esOps.search(ArgumentMatchers.any(NativeQuery.class), eq(NoteVectorDocument.class)))
                .thenReturn(stubHits);

        List<NoteVectorHit> hits = gateway.similar(new double[]{0.1, 0.2}, 5);

        assertEquals(2, hits.size());
        assertEquals(100L, hits.get(0).noteId());
        assertEquals(0.9f, hits.get(0).score().floatValue(), 1e-9);   // ES _score 为 float 拓宽
        assertEquals(200L, hits.get(1).noteId());
        assertEquals(0.8f, hits.get(1).score().floatValue(), 1e-9);

        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(esOps).search(captor.capture(), eq(NoteVectorDocument.class));
        String json = serialize(captor.getValue().getQuery());
        assertTrue(json.contains("\"field\":\"embedding\""), "kNN 应作用于 embedding 向量字段");
        assertTrue(json.contains("\"k\":5"), "Top-K 应为入参 size");
    }

    @Test
    @DisplayName("findVectorById — 向量缺失的文档 → empty（getter 返回 null 同无向量）")
    void shouldReturnEmpty_whenEmbeddingFieldMissing() {
        NoteVectorDocument document = new NoteVectorDocument();
        document.setNoteId(6L);
        assertNull(document.getEmbedding());
        when(esOps.get("6", NoteVectorDocument.class)).thenReturn(document);

        assertTrue(gateway.findVectorById(6L).isEmpty());
    }

    @SuppressWarnings("unchecked")
    private SearchHit<NoteVectorDocument> searchHit(long noteId, float score) {
        SearchHit<NoteVectorDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(noteDoc(noteId));
        when(hit.getScore()).thenReturn(score);
        return hit;
    }

    @SuppressWarnings("unchecked")
    private SearchHits<NoteVectorDocument> searchHits(List<SearchHit<NoteVectorDocument>> hits) {
        SearchHits<NoteVectorDocument> mockHits = mock(SearchHits.class);
        when(mockHits.getSearchHits()).thenReturn(hits);
        return mockHits;
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

    /** 类型化 matcher：消除 create(Request) / create(Function) 的重载歧义。 */
    private static Function<CreateIndexRequest.Builder, ObjectBuilder<CreateIndexRequest>> anyCreateFn() {
        return ArgumentMatchers.any();
    }

    /** 类型化 matcher：消除 exists(Request) / exists(Function) 的重载歧义。 */
    private static Function<ExistsRequest.Builder, ObjectBuilder<ExistsRequest>> anyExistsFn() {
        return ArgumentMatchers.any();
    }
}
