package org.icedamericanomall.search.infrastructure.es;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NoteVectorMapping} 单元测试 —— 笔记索引 mapping 由 dims 配置驱动（无魔法值）。
 *
 * <p>kNN 前置条件在此锁死：{@code index.knn=true} + {@code embedding.type=dense_vector,index=true}，
 * 维度取自 Embedding 配置（单一事实源），与商品/知识库 mapping 同构。
 */
@DisplayName("NoteVectorMapping — 笔记索引 mapping 由 dims 生成")
class NoteVectorMappingTest {

    @Test
    @DisplayName("生成 — 含 kNN 开关 + dense_vector 维度 + 原文字段")
    void shouldBuildKnnMapping_whenDimsProvided() {
        String json = NoteVectorMapping.buildJson(1536);

        assertTrue(json.contains("\"knn\": true"), "mapping 应开启 index.knn");
        assertTrue(json.contains("\"type\": \"dense_vector\""), "向量字段应为 dense_vector");
        assertTrue(json.contains("\"dims\": 1536"), "维度应来自配置");
        assertTrue(json.contains("\"index\": true"), "dense_vector 应开启 index（kNN 必需）");
        assertTrue(json.contains("\"similarity\": \"cosine\""), "相似度应为 cosine");
        assertTrue(json.contains("\"embedding\""), "向量字段名应为 embedding（与 NoteVectorDocument 一致）");
        assertTrue(json.contains("\"noteId\""), "应含 noteId（幂等键）");
        assertTrue(json.contains("\"title\""), "应含 title（向量化原文）");
        assertTrue(json.contains("\"content\""), "应含 content（向量化原文）");
    }

    @Test
    @DisplayName("生成 — dims 变化反映到 mapping（无硬编码维度）")
    void shouldReflectConfiguredDims() {
        assertTrue(NoteVectorMapping.buildJson(768).contains("\"dims\": 768"));
        assertTrue(NoteVectorMapping.buildJson(1024).contains("\"dims\": 1024"));
    }

    @Test
    @DisplayName("生成 — dims 非法（≤0）→ 建索引前即失败（fail-loud）")
    void shouldRejectNonPositiveDims() {
        assertThrows(IllegalArgumentException.class, () -> NoteVectorMapping.buildJson(0));
        assertThrows(IllegalArgumentException.class, () -> NoteVectorMapping.buildJson(-1));
    }
}
