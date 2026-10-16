package org.icedamericanomall.marketing.domain.service;

import org.icedamericanomall.marketing.domain.enums.ScopeTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code CouponScope} 纯领域服务测试 —— {@code coupon.scope_values} JSON 解析容错与
 * 「订单侧 ids 未传 ≠ 不限」的判定口径。
 */
@DisplayName("CouponScope — 适用范围判定与 JSON 形态容错")
class CouponScopeTest {

    // ==================== parseIds 形态容错 ====================

    @Test
    @DisplayName("parseIds — 字符串元素形态 [\"1\",\"2\"] → {1,2}")
    void shouldParseQuotedStringElements() {
        assertEquals(Set.of("1", "2"), CouponScope.parseIds("[\"1\",\"2\"]"));
    }

    @Test
    @DisplayName("parseIds — 数字元素形态 [1,2] → {1,2}（含空白容错）")
    void shouldParseNumericElements() {
        assertEquals(Set.of("1", "2"), CouponScope.parseIds("[1,2]"));
        assertEquals(Set.of("7", "8"), CouponScope.parseIds("[ 7 , 8 ]"));
    }

    @Test
    @DisplayName("parseIds — 被当作 JSON 标量存储的数组字符串 → 仍能解析")
    void shouldParseWrappedJsonString() {
        assertEquals(Set.of("1", "2"), CouponScope.parseIds("\"[\\\"1\\\",\\\"2\\\"]\""));
    }

    @Test
    @DisplayName("parseIds — null/空白/空数组 → 空集合")
    void shouldReturnEmpty_whenBlankOrEmptyArray() {
        assertTrue(CouponScope.parseIds(null).isEmpty());
        assertTrue(CouponScope.parseIds("   ").isEmpty());
        assertTrue(CouponScope.parseIds("[]").isEmpty());
    }

    @Test
    @DisplayName("splitIds — 逗号分隔串 → 集合（去空项）")
    void shouldSplitCsv() {
        assertEquals(Set.of("1001", "1002"), CouponScope.splitIds("1001,1002"));
        assertEquals(Set.of("1001"), CouponScope.splitIds("1001,,"));
        assertTrue(CouponScope.splitIds(null).isEmpty());
        assertTrue(CouponScope.splitIds("  ").isEmpty());
    }

    // ==================== accepts 判定 ====================

    @Test
    @DisplayName("accepts — scopeType=null → 放行（存量兼容）")
    void shouldAccept_whenScopeTypeNull() {
        assertTrue(CouponScope.accepts(null, "[\"1\"]", null, null));
    }

    @Test
    @DisplayName("accepts — scopeType=ALL 且 scopeValues 有值 → 放行（类型优先）")
    void shouldAccept_whenScopeTypeAll() {
        assertTrue(CouponScope.accepts(ScopeTypeEnum.ALL.getCode(), "[\"1\"]", null, null));
    }

    @Test
    @DisplayName("accepts — 有限定类型但 scopeValues 为空 → 放行（配置残缺不误伤）")
    void shouldAccept_whenScopeValuesEmpty() {
        assertTrue(CouponScope.accepts(ScopeTypeEnum.PRODUCT.getCode(), "[]", null, null));
        assertTrue(CouponScope.accepts(ScopeTypeEnum.CATEGORY.getCode(), null, null, null));
    }

    @Test
    @DisplayName("accepts — CATEGORY 命中 → 放行；未命中 → 拒绝")
    void shouldJudgeByCategory_whenScopeTypeCategory() {
        assertTrue(CouponScope.accepts(ScopeTypeEnum.CATEGORY.getCode(), "[\"7\",\"8\"]", null, "8,9"));
        assertFalse(CouponScope.accepts(ScopeTypeEnum.CATEGORY.getCode(), "[\"7\",\"8\"]", null, "9"));
    }

    @Test
    @DisplayName("accepts — PRODUCT 命中 → 放行；未命中 → 拒绝")
    void shouldJudgeBySpu_whenScopeTypeSpu() {
        assertTrue(CouponScope.accepts(ScopeTypeEnum.PRODUCT.getCode(), "[\"1001\"]", "1001,1002", null));
        assertFalse(CouponScope.accepts(ScopeTypeEnum.PRODUCT.getCode(), "[\"1001\"]", "2001", null));
    }

    @Test
    @DisplayName("accepts — 有限定范围但订单侧 ids 未传 → 拒绝（漏传不等于不限）")
    void shouldReject_whenScopeLimitedButOrderIdsMissing() {
        assertFalse(CouponScope.accepts(ScopeTypeEnum.PRODUCT.getCode(), "[\"1001\"]", null, null));
        assertFalse(CouponScope.accepts(ScopeTypeEnum.CATEGORY.getCode(), "[\"7\"]", null, ""));
    }
}
