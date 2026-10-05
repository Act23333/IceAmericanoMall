package org.icedamericanomall.item.domain.model.kg;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BrandKeys 单元测试 —— brandKey 规范化归并规则与 CRC-32 正 Long 节点 id 口径
 * （F15 O4 值节点裁决，口径见类注释；增量摄取与全量重建共用同一规则，必须稳定）。
 */
@DisplayName("BrandKeys — 品牌值节点规范化与节点 id")
class BrandKeysTest {

    @Test
    @DisplayName("brandKey — 大小写/首尾空格/连续空白差异归并为同一 key")
    void shouldNormalizeToSameKey_whenCasingAndWhitespaceDiffer() {
        assertEquals("ice americano", BrandKeys.brandKey("  Ice   AMERICANO "));
        assertEquals(BrandKeys.brandKey("IceAmericano"), BrandKeys.brandKey("iceamericano"));
    }

    @Test
    @DisplayName("brandKey — 空白入参返回 null（调用方据此跳过 BRAND_OF 边）")
    void shouldReturnNull_whenBrandBlank() {
        assertNull(BrandKeys.brandKey(null));
        assertNull(BrandKeys.brandKey("   "));
    }

    @Test
    @DisplayName("nodeId — 同 key 恒得同 id（增量/重建幂等一致），且恒为正 Long")
    void shouldBeStableAndPositive_whenSameKey() {
        Long first = BrandKeys.nodeId("冰美 Brand");
        Long second = BrandKeys.nodeId(" 冰美 brand ");
        assertEquals(first, second);
        assertNotNull(first);
        assertTrue(first > 0, "CRC-32 无符号取值必须为正 Long: " + first);
    }

    @Test
    @DisplayName("nodeId — 空白入参返回 null")
    void shouldReturnNullNodeId_whenBrandBlank() {
        assertNull(BrandKeys.nodeId(""));
    }
}
