package org.icedamericanomall.search.app;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.search.domain.gateway.EmbeddingGateway;
import org.icedamericanomall.search.domain.gateway.KnowledgeVectorIndexGateway;
import org.icedamericanomall.search.domain.model.search.KnowledgeChunkHit;
import org.icedamericanomall.search.domain.service.DocumentChunker;
import org.icedamericanomall.search.infrastructure.config.KnowledgeVectorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * KnowledgeVectorManager 单元测试 —— 摄取（分块/向量化/幂等重写）、删除、检索与降级。
 *
 * <p>Mock ES 索引网关（{@link KnowledgeVectorIndexGateway}）与 Embedding 网关
 * （{@link EmbeddingGateway}），使用真实 {@link DocumentChunker}（领域服务）验证分块计数。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeVectorManagerTest {

    @Mock
    private EmbeddingGateway embeddingGateway;

    @Mock
    private KnowledgeVectorIndexGateway indexGateway;

    private KnowledgeVectorProperties indexProperties;

    private KnowledgeVectorManager manager;

    @BeforeEach
    void setUp() {
        indexProperties = new KnowledgeVectorProperties();
        indexProperties.setIndexName("knowledge_vectors");
        indexProperties.setMaxChunks(50);
        manager = new KnowledgeVectorManager(embeddingGateway, indexGateway,
                new DocumentChunker(), indexProperties);
        when(embeddingGateway.embed(any())).thenReturn(new float[]{0.1f, 0.2f});
    }

    @Test
    @DisplayName("摄取 — 两个 Q&A 分块 → 逐块向量化并返回 chunk 数")
    void shouldEmbedEachChunk_whenUpsert() {
        when(embeddingGateway.isConfigured()).thenReturn(true);

        int chunks = manager.upsert(100L, 9L, "退货政策",
                "Q1:怎么退货？30天内可退。Q2:运费谁承担？商家承担运费。");

        assertEquals(2, chunks);
        verify(embeddingGateway, times(2)).embed(any());
        verify(indexGateway).indexChunks(eq(100L), eq(9L), any(), any());
    }

    @Test
    @DisplayName("摄取 — 幂等口径：先建索引 → 先删后写（顺序固定）")
    void shouldDeleteBeforeIndex_whenUpsert() {
        when(embeddingGateway.isConfigured()).thenReturn(true);

        manager.upsert(100L, 9L, "标题", "普通段落内容。");

        InOrder order = inOrder(indexGateway);
        order.verify(indexGateway).ensureIndex();
        order.verify(indexGateway).deleteByKnowledgeId(100L);
        order.verify(indexGateway).indexChunks(eq(100L), eq(9L), any(), any());
    }

    @Test
    @DisplayName("摄取 — Embedding 未配置 → 返回 0 且不调用向量化/ES（未启用不报错）")
    void shouldReturnZero_whenEmbeddingNotConfigured() {
        when(embeddingGateway.isConfigured()).thenReturn(false);

        int chunks = manager.upsert(100L, 9L, "标题", "内容");

        assertEquals(0, chunks);
        verifyNoInteractions(indexGateway);
        verify(embeddingGateway, never()).embed(any());
    }

    @Test
    @DisplayName("摄取 — Embedding 调用失败 → 抛 BizException（调用方降级），不写 ES")
    void shouldThrowBizException_whenEmbeddingFails() {
        when(embeddingGateway.isConfigured()).thenReturn(true);
        when(embeddingGateway.embed(any()))
                .thenThrow(new BizException(ErrorCode.THIRD_PARTY_CONNECT_FAILED, "embedding down"));

        assertThrows(BizException.class, () -> manager.upsert(100L, 9L, "标题", "内容"));
        verify(indexGateway, never()).indexChunks(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("摄取 — knowledgeId 为空 → BadRequestException")
    void shouldRejectUpsert_whenKnowledgeIdMissing() {
        assertThrows(BadRequestException.class, () -> manager.upsert(null, 9L, "标题", "内容"));
    }

    @Test
    @DisplayName("摄取 — 分块数超上限 → 按 maxChunks 截断")
    void shouldTruncateChunks_whenOverLimit() {
        when(embeddingGateway.isConfigured()).thenReturn(true);
        indexProperties.setMaxChunks(1);

        int chunks = manager.upsert(100L, 9L, "标题",
                "Q1:问题一？答案一。Q2:问题二？答案二。Q3:问题三？答案三。");

        assertEquals(1, chunks);
        verify(embeddingGateway, times(1)).embed(any());
    }

    @Test
    @DisplayName("摄取 — sellerId 随 chunk 一并落库（商家隔离依据）")
    void shouldPassSellerIdToIndex() {
        when(embeddingGateway.isConfigured()).thenReturn(true);

        manager.upsert(100L, 42L, "标题", "内容");

        ArgumentCaptor<Long> sellerCaptor = ArgumentCaptor.forClass(Long.class);
        verify(indexGateway).indexChunks(anyLong(), sellerCaptor.capture(), any(), any());
        assertEquals(42L, sellerCaptor.getValue());
    }

    @Test
    @DisplayName("删除 — 先建索引再按 knowledgeId 删除")
    void shouldDeleteByKnowledgeId_whenDelete() {
        manager.delete(100L);

        InOrder order = inOrder(indexGateway);
        order.verify(indexGateway).ensureIndex();
        order.verify(indexGateway).deleteByKnowledgeId(100L);
    }

    @Test
    @DisplayName("检索 — 向量为空 → 返回空列表且不触达 ES")
    void shouldReturnEmpty_whenQueryVectorEmpty() {
        assertTrue(manager.searchKnowledge(List.of(), 5, 9L).isEmpty());
        verifyNoInteractions(indexGateway);
    }

    @Test
    @DisplayName("检索 — 命中 → 返回领域命中（含 knowledgeId/chunkIndex/text）")
    void shouldReturnHits_whenSearch() {
        when(indexGateway.knnSearch(any(), anyInt(), anyLong())).thenReturn(List.of(
                new KnowledgeChunkHit(100L, 0, 9L, "退货政策", "30天内可退")));

        List<KnowledgeChunkHit> hits = manager.searchKnowledge(List.of(0.1, 0.2), 5, 9L);

        assertEquals(1, hits.size());
        assertEquals(100L, hits.get(0).knowledgeId());
        assertEquals(0, hits.get(0).chunkIndex());
        assertEquals("30天内可退", hits.get(0).text());
    }

    @Test
    @DisplayName("检索 — 索引网关返回空 → 空结果（等价无命中降级）")
    void shouldReturnEmpty_whenNoHit() {
        when(indexGateway.knnSearch(any(), anyInt(), anyLong())).thenReturn(List.of());

        assertTrue(manager.searchKnowledge(List.of(0.1), 5, 9L).isEmpty());
    }
}
