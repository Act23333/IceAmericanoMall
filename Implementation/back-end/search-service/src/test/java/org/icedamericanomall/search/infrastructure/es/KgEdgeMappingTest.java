package org.icedamericanomall.search.infrastructure.es;

import org.icedamericanomall.search.domain.model.kg.KgEdgeDoc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * KgEdgeMapping 纯函数测试 —— 锁死「keyword 精确字段 + 数值字段 + 非向量」与 edgeId 幂等口径。
 */
class KgEdgeMappingTest {

    @Test
    @DisplayName("索引名 — 固定为契约索引 kg_edges")
    void shouldUseContractIndexName() {
        assertEquals("kg_edges", KgEdgeMapping.INDEX_NAME);
    }

    @Test
    @DisplayName("mapping — 精确检索字段为 keyword，数值字段为 long/integer")
    void shouldBuildKeywordAndNumericMapping() {
        String json = KgEdgeMapping.buildJson();

        assertTrue(json.contains("\"edgeId\""));
        assertTrue(json.contains("\"srcType\""));
        assertTrue(json.contains("\"relType\""));
        assertTrue(json.contains("\"dstType\""));
        assertTrue(json.contains("\"source\""));
        assertTrue(json.contains("\"type\": \"keyword\""));
        assertTrue(json.contains("\"srcId\""));
        assertTrue(json.contains("\"dstId\""));
        assertTrue(json.contains("\"updateTime\""));
        assertTrue(json.contains("\"type\": \"long\""));
        assertTrue(json.contains("\"weight\""));
        assertTrue(json.contains("\"status\""));
        assertTrue(json.contains("\"type\": \"integer\""));
    }

    @Test
    @DisplayName("mapping — 非向量索引：不含 dense_vector / knn 设置")
    void shouldNotContainVectorFields() {
        String json = KgEdgeMapping.buildJson();

        assertFalse(json.contains("dense_vector"));
        assertFalse(json.contains("knn"));
    }

    @Test
    @DisplayName("edgeId — 按 srcType:srcId:relType:dstType:dstId 派生（PRD §16.4 示例口径）")
    void shouldDeriveEdgeId_whenEdgeGiven() {
        KgEdgeDoc edge = new KgEdgeDoc("product", 1001L, "BELONGS_TO", "category", 10L, 0, "DIRECT", 1);

        assertEquals("product:1001:BELONGS_TO:category:10", KgEdgeMapping.edgeId(edge));
    }
}
