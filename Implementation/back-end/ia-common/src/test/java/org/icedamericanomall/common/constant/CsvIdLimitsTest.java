package org.icedamericanomall.common.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 承载容量常量族测试（{@code CsvIdLimits}，批次 B7b；ADR-063 §守卫 6 的常量侧守卫）。
 *
 * <p>锁三件事：
 * <ol>
 *   <li><b>取值即算式结果</b>：15 / 4 / 200 / 200 —— 每个值都能由「承载容量 ÷ 33 字符/项」倒推；</li>
 *   <li><b>三条判据不互借</b>：列宽判据（15）≠ URL 判据（4）≠ body 判据（200）；
 *       若有人把某一项改成另一个值「统一一下」，本测试即红；</li>
 *   <li><b>body 上限的自洽</b>：订单明细上限 ≤ 购物车条目上限（明细来源是购物车/直购）。</li>
 * </ol>
 */
@DisplayName("CsvIdLimits —— 承载容量常量族（列宽 / URL / body 三条判据不互借）")
class CsvIdLimitsTest {

    @Test
    @DisplayName("取值 — 15（列宽 500÷33）/ 4（URL 预算）/ 200（请求体预算）/ 200（明细 ≤ 购物车）")
    void shouldHaveExpectedValues() {
        assertEquals(15, CsvIdLimits.MAX_CSV_IDS_IN_COLUMN);
        assertEquals(4, CsvIdLimits.MAX_COMPARE_SPU_IDS);
        assertEquals(200, CsvIdLimits.MAX_CART_ITEMS);
        assertEquals(200, CsvIdLimits.MAX_ORDER_ITEM_IDS);
    }

    @Test
    @DisplayName("列宽判据的算式自洽 — 15×32+14=494 ≤ 500，16×32+15=527 > 500")
    void shouldMatchColumnArithmetic() {
        int n = CsvIdLimits.MAX_CSV_IDS_IN_COLUMN;
        assertTrue(n * 32 + (n - 1) <= 500, "上限项数必须装得进 VARCHAR(500)");
        assertTrue((n + 1) * 32 + n > 500, "上限 +1 项必须装不下（否则上限拍小了）");
    }

    @Test
    @DisplayName("三条判据不得互借 — 列宽 / URL / body 三个上限两两不同")
    void shouldNotBorrowCriteria() {
        assertTrue(CsvIdLimits.MAX_CSV_IDS_IN_COLUMN != CsvIdLimits.MAX_COMPARE_SPU_IDS,
                "列宽判据与 URL 判据必须是不同的数（互借即本类缺陷的复发形态）");
        assertTrue(CsvIdLimits.MAX_CART_ITEMS > CsvIdLimits.MAX_CSV_IDS_IN_COLUMN,
                "请求体预算远大于列宽预算（否则有人把列宽值搬去校验 body）");
    }

    @Test
    @DisplayName("body 自洽 — 订单明细上限 ≤ 购物车条目上限")
    void shouldKeepOrderItemsWithinCartLimit() {
        assertTrue(CsvIdLimits.MAX_ORDER_ITEM_IDS <= CsvIdLimits.MAX_CART_ITEMS);
    }
}
