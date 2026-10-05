package org.icedamericanomall.search.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ProductVectorTextBuilder} 单元测试 —— 商品向量化文本拼接口径在此锁死
 * （I47 单向量摄取与 reindex 全量重建共用，ADR-046 §2）。
 */
@DisplayName("ProductVectorTextBuilder — 商品向量化文本拼接")
class ProductVectorTextBuilderTest {

    @Test
    @DisplayName("拼接 — name + description + brand（单空格分隔，段内 trim）")
    void shouldConcatAllParts_whenAllProvided() {
        assertEquals("无线蓝牙耳机 主动降噪 SoundMax",
                ProductVectorTextBuilder.build("无线蓝牙耳机 ", " 主动降噪", "SoundMax "));
    }

    @Test
    @DisplayName("拼接 — 可选段空白/null → 只用 name（无尾随空格）")
    void shouldUseNameOnly_whenOptionalPartsBlank() {
        assertEquals("无线蓝牙耳机", ProductVectorTextBuilder.build("无线蓝牙耳机", "  ", null));
        assertEquals("无线蓝牙耳机", ProductVectorTextBuilder.build("无线蓝牙耳机", null, ""));
    }

    @Test
    @DisplayName("拼接 — 全空白 → 空串（null 安全）")
    void shouldReturnEmpty_whenAllBlank() {
        assertEquals("", ProductVectorTextBuilder.build(null, null, null));
    }
}
