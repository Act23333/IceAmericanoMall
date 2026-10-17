package org.icedamericanomall.item.domain.model.home;

import org.icedamericanomall.common.constant.CsvIdLimits;
import org.icedamericanomall.common.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code home_config.spu_ids} 读写口径测试（ADR-063 §决策 7 表 #3，批次 B7b）——
 * 锁定<b>写侧 fail-closed / 读侧 fail-open</b> 的刻意不对称，以及列宽容量预算（守卫 6）。
 */
@DisplayName("SpuIdCsv —— 写侧 fail-closed / 读侧 fail-open / 容量预算")
class SpuIdCsvTest {

    /** 合法 SPU 业务键（32-hex）夹具：第 i 个。 */
    private static String key(int i) {
        return String.format("%032x", i);
    }

    // ==================== 写侧 ====================

    @Test
    @DisplayName("normalizeForWrite — 合法 CSV 原样保留顺序，去空白项")
    void shouldKeepValidCsv() {
        String csv = key(1) + "," + key(2);
        assertEquals(csv, SpuIdCsv.normalizeForWrite(" " + key(1) + " , " + key(2) + " ,"));
    }

    @Test
    @DisplayName("normalizeForWrite — 重复项归一（同一商品在首屏出现两次是配置错误）")
    void shouldDeduplicate() {
        assertEquals(key(1) + "," + key(2),
                SpuIdCsv.normalizeForWrite(key(1) + "," + key(2) + "," + key(1)));
    }

    @Test
    @DisplayName("normalizeForWrite — 空/空白 → null（不落空串）")
    void shouldNormalizeBlankToNull() {
        assertNull(SpuIdCsv.normalizeForWrite(null));
        assertNull(SpuIdCsv.normalizeForWrite("   "));
        assertNull(SpuIdCsv.normalizeForWrite(",,"));
    }

    @Test
    @DisplayName("normalizeForWrite — 任一项形态非法 → 400 PARAM_ERROR（写入侧不静默丢项）")
    void shouldRejectMalformedItem() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> SpuIdCsv.normalizeForWrite(key(1) + "," + "7"));
        assertEquals(1101, ex.getErrorCode().getCode());
        // 旧技术键形态（数字）与半截 hex 都必须被拒：写错应立刻改，而不是留到首屏
        assertThrows(BadRequestException.class, () -> SpuIdCsv.normalizeForWrite("12345"));
    }

    @Test
    @DisplayName("normalizeForWrite — 超过 15 项 → 400（列宽 VARCHAR(500) 倒推的上限，不借 URL 判据）")
    void shouldRejectOverflow() {
        String sixteen = IntStream.rangeClosed(1, CsvIdLimits.MAX_CSV_IDS_IN_COLUMN + 1)
                .mapToObj(SpuIdCsvTest::key).collect(Collectors.joining(","));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> SpuIdCsv.normalizeForWrite(sixteen));
        assertEquals(1101, ex.getErrorCode().getCode());
        // 恰好 15 项必须通过（边界不是 14）
        String fifteen = IntStream.rangeClosed(1, CsvIdLimits.MAX_CSV_IDS_IN_COLUMN)
                .mapToObj(SpuIdCsvTest::key).collect(Collectors.joining(","));
        assertEquals(15, SpuIdCsv.normalizeForWrite(fifteen).split(",").length);
    }

    // ==================== 读侧 ====================

    @Test
    @DisplayName("filterForRead — 个别项非法只丢该项，其余保留（首屏坏一条不得拖垮首页）")
    void shouldDropInvalidItemsOnly() {
        assertEquals(key(1) + "," + key(3),
                SpuIdCsv.filterForRead(key(1) + ",7," + key(3)));
    }

    @Test
    @DisplayName("filterForRead — 全非法 → null（该配置的商品位降级为空，绝不抛错）")
    void shouldReturnNullWhenAllInvalid() {
        assertNull(SpuIdCsv.filterForRead("7,8,old_product_1"));
        assertNull(SpuIdCsv.filterForRead(null));
        assertNull(SpuIdCsv.filterForRead(""));
    }

    // ==================== 容量预算（守卫 6） ====================

    @Test
    @DisplayName("容量预算 — 15 项 × 32 + 14 分隔符 = 494 ≤ VARCHAR(500)；16 项 = 527 即溢出")
    void shouldFitColumnBudget() {
        int n = CsvIdLimits.MAX_CSV_IDS_IN_COLUMN;
        int lengthAtLimit = n * 32 + (n - 1);
        assertEquals(494, lengthAtLimit);
        assertTrue(lengthAtLimit <= 500, "15 项必须装得进 VARCHAR(500)");

        int lengthBeyond = (n + 1) * 32 + n;
        assertEquals(527, lengthBeyond);
        assertTrue(lengthBeyond > 500, "16 项确实溢出 —— 上限不是拍脑袋值");
    }
}
