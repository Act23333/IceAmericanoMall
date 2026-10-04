package org.icedamericanomall.fallback;

import org.icedamericanomall.client.KnowledgeVectorClient;
import org.icedamericanomall.dto.KnowledgeVectorCO;
import org.icedamericanomall.dto.KnowledgeVectorSearchCO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link KnowledgeVectorClientFallback} 降级语义测试 —— fail-open（返回失败哨兵，不抛异常）。
 *
 * <p>锁死：摄取降级为 {@code -1}、删除降级为 {@code false}、检索降级为空列表，
 * 三者都<b>不抛异常</b>（否则会打断 ai-service 的知识库 CRUD 与客服应答）。
 */
@DisplayName("KnowledgeVectorClientFallback — fail-open 哨兵")
class KnowledgeVectorClientFallbackTest {

    private final KnowledgeVectorClient client =
            new KnowledgeVectorClientFallback().create(new RuntimeException("search-service down"));

    @Test
    @DisplayName("摄取降级 — 返回 -1 哨兵（调用方不写 embeddingId）")
    void shouldReturnSentinel_whenUpsertDegrades() {
        KnowledgeVectorCO co = new KnowledgeVectorCO();
        co.setKnowledgeId(1L);

        assertEquals(-1, client.upsertKnowledgeVectors(co));
    }

    @Test
    @DisplayName("摄取降级 — 入参为 null 也不抛异常")
    void shouldNotThrow_whenUpsertDegradesWithNullPayload() {
        assertEquals(-1, client.upsertKnowledgeVectors(null));
    }

    @Test
    @DisplayName("删除降级 — 返回 false")
    void shouldReturnFalse_whenDeleteDegrades() {
        assertFalse(client.deleteKnowledgeVectors(1L));
    }

    @Test
    @DisplayName("检索降级 — 返回空列表（等价无命中）")
    void shouldReturnEmpty_whenSearchDegrades() {
        KnowledgeVectorSearchCO co = new KnowledgeVectorSearchCO();
        co.setIndex(KnowledgeVectorClient.INDEX_KNOWLEDGE_VECTORS);

        List<?> result = client.vectorSearch(co);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
