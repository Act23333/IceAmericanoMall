package org.icedamericanomall.search.infrastructure.es;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ProductVectorMapping} 单元测试 —— 商品索引 mapping 由 dims 配置驱动（无魔法值）。
 *
 * <p>ES 7.17 dense_vector 的 kNN 前置条件在此锁死：{@code index.knn=true} +
 * {@code embedding.type=dense_vector,index=true}，维度取自 Embedding 配置（单一事实源）。
 */
@DisplayName("ProductVectorMapping — 商品索引 mapping 由 dims 生成")
class ProductVectorMappingTest {

    @Test
    @DisplayName("生成 — 含 kNN 开关 + dense_vector 维度 + 检索字段")
    void shouldBuildKnnMapping_whenDimsProvided() {
        String json = ProductVectorMapping.buildJson(1536);

        assertTrue(json.contains("\"knn\": true"), "mapping 应开启 index.knn");
        assertTrue(json.contains("\"type\": \"dense_vector\""), "向量字段应为 dense_vector");
        assertTrue(json.contains("\"dims\": 1536"), "维度应来自配置");
        assertTrue(json.contains("\"index\": true"), "dense_vector 应开启 index（kNN 必需）");
        assertTrue(json.contains("\"similarity\": \"cosine\""), "相似度应为 cosine");
        assertTrue(json.contains("\"embedding\""), "向量字段名应为 embedding（与 ProductDocument 一致）");
        assertTrue(json.contains("\"name\""), "应含 name（I28 关键词检索字段）");
        assertTrue(json.contains("\"description\""), "应含 description（I28 关键词检索字段）");
        assertTrue(json.contains("\"price\""), "应含 price（展示字段）");
    }

    @Test
    @DisplayName("生成 — dims 变化反映到 mapping（无硬编码维度）")
    void shouldReflectConfiguredDims() {
        assertTrue(ProductVectorMapping.buildJson(768).contains("\"dims\": 768"));
        assertTrue(ProductVectorMapping.buildJson(1024).contains("\"dims\": 1024"));
    }

    @Test
    @DisplayName("生成 — dims 非法（≤0）→ 建索引前即失败（fail-loud）")
    void shouldRejectNonPositiveDims() {
        assertThrows(IllegalArgumentException.class, () -> ProductVectorMapping.buildJson(0));
        assertThrows(IllegalArgumentException.class, () -> ProductVectorMapping.buildJson(-1));
    }
}
