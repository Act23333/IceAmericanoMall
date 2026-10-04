package org.icedamericanomall.search.infrastructure.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.util.ObjectBuilder;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.search.infrastructure.config.EmbeddingProperties;
import org.icedamericanomall.search.infrastructure.config.KnowledgeVectorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KnowledgeVectorIndexAdapter 索引生命周期测试 —— mock ES 客户端。
 *
 * <p>锁死「索引不存在才创建 + 并发建索引无害化 + 建索引失败 fail-loud」三条不变量。
 * 由于 {@code ElasticsearchIndicesClient} 的 {@code exists/create} 各有
 * 『Request 重载』与『Function 重载』两支（调用点用后者），故 matcher 需显式类型化以免歧义。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeVectorIndexAdapterTest {

    @Mock
    private ElasticsearchClient esClient;

    @Mock
    private ElasticsearchIndicesClient indicesClient;

    private KnowledgeVectorIndexAdapter support;

    @BeforeEach
    void setUp() {
        KnowledgeVectorProperties indexProperties = new KnowledgeVectorProperties();
        indexProperties.setIndexName("knowledge_vectors");
        EmbeddingProperties embeddingProperties = new EmbeddingProperties();
        embeddingProperties.setDims(1536);
        support = new KnowledgeVectorIndexAdapter(esClient, indexProperties, embeddingProperties);
        when(esClient.indices()).thenReturn(indicesClient);
    }

    @Test
    @DisplayName("建索引 — 索引不存在 → 按配置 dims 创建")
    void shouldCreateIndex_whenMissing() throws Exception {
        BooleanResponse notExists = existsResponse(false);
        CreateIndexResponse created = mock(CreateIndexResponse.class);
        when(indicesClient.exists(anyExistsFn())).thenReturn(notExists);
        when(indicesClient.create(anyCreateFn())).thenReturn(created);

        support.ensureIndex();

        verify(indicesClient).create(anyCreateFn());
    }

    @Test
    @DisplayName("建索引 — 索引已存在 → 跳过创建（幂等）")
    void shouldSkipCreate_whenIndexExists() throws Exception {
        BooleanResponse exists = existsResponse(true);
        when(indicesClient.exists(anyExistsFn())).thenReturn(exists);

        support.ensureIndex();

        verify(indicesClient, never()).create(anyCreateFn());
    }

    @Test
    @DisplayName("建索引 — 并发已被他人创建（resource_already_exists）→ 静默通过")
    void shouldSwallowAlreadyExists_whenConcurrentCreate() throws Exception {
        BooleanResponse notExists = existsResponse(false);
        when(indicesClient.exists(anyExistsFn())).thenReturn(notExists);
        when(indicesClient.create(anyCreateFn())).thenThrow(new RuntimeException(
                "resource_already_exists_exception: index [knowledge_vectors] already exists"));

        support.ensureIndex();

        verify(indicesClient).create(anyCreateFn());
    }

    @Test
    @DisplayName("建索引 — 其它 ES 故障 → 抛 BizException（fail-loud，由调用方降级）")
    void shouldThrowBizException_whenCreateFails() throws Exception {
        BooleanResponse notExists = existsResponse(false);
        when(indicesClient.exists(anyExistsFn())).thenReturn(notExists);
        when(indicesClient.create(anyCreateFn())).thenThrow(new RuntimeException("connection refused"));

        assertThrows(BizException.class, () -> support.ensureIndex());
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
}
