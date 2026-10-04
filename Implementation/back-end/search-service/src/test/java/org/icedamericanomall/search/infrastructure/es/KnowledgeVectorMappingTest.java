package org.icedamericanomall.search.infrastructure.es;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link KnowledgeVectorMapping} 单元测试 —— 索引 mapping 由 dims 配置驱动（无魔法值）。
 *
 * <p>ES 7.17 dense_vector 的 kNN 前置条件在此锁死：{@code index.knn=true} +
 * {@code vector.type=dense_vector,index=true}，维度取自配置。
 */
@DisplayName("KnowledgeVectorMapping — 索引 mapping 由 dims 生成")
class KnowledgeVectorMappingTest {

    @Test
    @DisplayName("生成 — 含 kNN 开关 + dense_vector 维度 + 过滤字段")
    void shouldBuildKnnMapping_whenDimsProvided() {
        String json = KnowledgeVectorMapping.buildJson(1536);

        assertTrue(json.contains("\"knn\": true"), "mapping 应开启 index.knn");
        assertTrue(json.contains("\"type\": \"dense_vector\""), "向量字段应为 dense_vector");
        assertTrue(json.contains("\"dims\": 1536"), "维度应来自配置");
        assertTrue(json.contains("\"index\": true"), "dense_vector 应开启 index（kNN 必需）");
        assertTrue(json.contains("\"similarity\": \"cosine\""), "相似度应为 cosine");
        assertTrue(json.contains("knowledgeId"), "应含 knowledgeId（删除按 term 匹配）");
        assertTrue(json.contains("sellerId"), "应含 sellerId（检索过滤）");
        assertTrue(json.contains("chunkIndex"), "应含 chunkIndex");
        assertTrue(json.contains("\"vector\""), "向量字段名应为 vector（与写入/检索一致）");
    }

    @Test
    @DisplayName("生成 — dims 变化反映到 mapping（无硬编码维度）")
    void shouldReflectConfiguredDims() {
        assertTrue(KnowledgeVectorMapping.buildJson(768).contains("\"dims\": 768"));
        assertTrue(KnowledgeVectorMapping.buildJson(1024).contains("\"dims\": 1024"));
    }

    @Test
    @DisplayName("生成 — dims 非法（≤0）→ 建索引前即失败（fail-loud）")
    void shouldRejectNonPositiveDims() {
        assertThrows(IllegalArgumentException.class, () -> KnowledgeVectorMapping.buildJson(0));
        assertThrows(IllegalArgumentException.class, () -> KnowledgeVectorMapping.buildJson(-1));
    }
}
