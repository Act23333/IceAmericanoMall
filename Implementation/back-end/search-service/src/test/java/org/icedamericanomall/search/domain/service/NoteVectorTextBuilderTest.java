package org.icedamericanomall.search.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * NoteVectorTextBuilder 单元测试 —— I50 向量化文本拼接口径（title + content，可选拼接不产生尾随空格）。
 */
@DisplayName("NoteVectorTextBuilder — 笔记向量化文本拼接")
class NoteVectorTextBuilderTest {

    @Test
    @DisplayName("拼接 — title + content，单个空格分隔")
    void shouldJoinTitleAndContent() {
        assertEquals("冲咖啡入门指南 手冲水温 90 度",
                NoteVectorTextBuilder.build("冲咖啡入门指南", "手冲水温 90 度"));
    }

    @Test
    @DisplayName("拼接 — content 空白/为 null → 只用 title（不产生尾随空格）")
    void shouldUseTitleOnly_whenContentBlank() {
        assertEquals("冲咖啡入门指南", NoteVectorTextBuilder.build("冲咖啡入门指南", "  "));
        assertEquals("冲咖啡入门指南", NoteVectorTextBuilder.build("冲咖啡入门指南", null));
    }

    @Test
    @DisplayName("拼接 — title 为 null → 只用 content")
    void shouldUseContentOnly_whenTitleNull() {
        assertEquals("手冲水温 90 度", NoteVectorTextBuilder.build(null, "手冲水温 90 度"));
    }

    @Test
    @DisplayName("拼接 — 各段 trim（首尾空白不进向量文本）")
    void shouldTrimParts() {
        assertEquals("标题 内容", NoteVectorTextBuilder.build("  标题  ", " 内容 "));
    }

    @Test
    @DisplayName("拼接 — 各段均空白 → 空串")
    void shouldReturnEmpty_whenAllPartsBlank() {
        assertEquals("", NoteVectorTextBuilder.build(null, "   "));
    }
}
