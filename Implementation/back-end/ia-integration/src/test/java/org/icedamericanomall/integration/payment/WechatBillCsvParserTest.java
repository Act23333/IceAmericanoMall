package org.icedamericanomall.integration.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 微信 v3 交易账单 CSV 解析测试（F4 渠道对账）。
 *
 * <p>锁死两条口径：①关键列位置（5=微信订单号、6=商户订单号、24=订单金额）；
 * ②金额「元 → 分」为定点字符串换算（无浮点误差，符合「金额一律整数分」硬约束）。
 */
@DisplayName("微信账单 CSV 解析 — 列定位 + 元转分")
class WechatBillCsvParserTest {

    /** 微信交易账单表头（真实形态为 27 列，此处保留关键列位）。 */
    private static final String HEADER = "交易时间,公众账号ID,商户号,特约商户号,设备号,微信订单号,商户订单号,"
            + "用户标识,交易类型,交易状态,付款银行,货币种类,应结订单金额,代金券金额,微信退款单号,商户退款单号,"
            + "退款金额,充值券退款金额,退款类型,退款状态,商品名称,商户数据包,手续费,费率,订单金额,申请退款金额,费率备注";

    private static String row(String tradeTime, String txnId, String outTradeNo, String orderAmount) {
        return String.join(",", tradeTime, "wxa1", "1900000109", "", "", txnId, outTradeNo,
                "oUser1", "NATIVE", "SUCCESS", "OTHERS", "CNY", orderAmount, "0.00", "", "",
                "0.00", "0.00", "", "", "商品", "data", "0.10", "0.60%", orderAmount, "0.00", "");
    }

    @Test
    @DisplayName("解析 — 表头/汇总行被跳过，数据行提取 商户订单号 + 渠道交易号 + 金额（分）")
    void shouldParseBillRows_whenHeaderAndSummaryPresent() {
        String csv = String.join("\n",
                HEADER,
                row("2026-10-02 10:00:00", "4200000TXN1", "pay-001", "99.00"),
                row("2026-10-02 11:00:00", "4200000TXN2", "pay-002", "128.00"),
                ",,,,总交易单数,3,,,总交易额,102526.00");

        List<ChannelBillItem> items = WechatBillCsvParser.parse(csv);

        assertEquals(2, items.size(), "表头与汇总行必须被跳过");
        assertEquals("pay-001", items.get(0).outTradeNo());
        assertEquals("4200000TXN1", items.get(0).channelTxnId());
        assertEquals(9900, items.get(0).amount());
        assertEquals(12800, items.get(1).amount());
    }

    @Test
    @DisplayName("解析 — 含 UTF-8 BOM 与 CRLF 换行仍可解析")
    void shouldParse_whenBomAndCrLfPresent() {
        String csv = "\uFEFF" + HEADER + "\r\n" + row("2026-10-02 10:00:00", "TXN", "pay-001", "1.00");

        List<ChannelBillItem> items = WechatBillCsvParser.parse(csv);

        assertEquals(1, items.size());
        assertEquals(100, items.get(0).amount());
    }

    @Test
    @DisplayName("解析 — 空文本/空白 → 空列表（不抛异常）")
    void shouldReturnEmpty_whenBlank() {
        assertTrue(WechatBillCsvParser.parse(null).isEmpty());
        assertTrue(WechatBillCsvParser.parse("   ").isEmpty());
    }

    @Test
    @DisplayName("解析 — 缺商户订单号的行被跳过（防脏数据进对账明细）")
    void shouldSkipRow_whenOutTradeNoMissing() {
        String csv = String.join("\n", HEADER, row("2026-10-02 10:00:00", "TXN", "", "9.00"));

        assertTrue(WechatBillCsvParser.parse(csv).isEmpty());
    }

    @Test
    @DisplayName("元转分 — 整数 / 一位小数 / 两位小数 / 空值 / 非法值")
    void shouldConvertYuanToCents() {
        assertEquals(12800, WechatBillCsvParser.yuanToCents("128.00"));
        assertEquals(12850, WechatBillCsvParser.yuanToCents("128.5"));
        assertEquals(12800, WechatBillCsvParser.yuanToCents("128"));
        assertEquals(1, WechatBillCsvParser.yuanToCents("0.01"));
        assertEquals(0, WechatBillCsvParser.yuanToCents(""));
        assertEquals(0, WechatBillCsvParser.yuanToCents(null));
        assertEquals(0, WechatBillCsvParser.yuanToCents("abc"), "脏数据按 0 处理，不中断整批解析");
    }

    @Test
    @DisplayName("解析 — 渠道交易号缺失 → null（不是空串）")
    void shouldReturnNullChannelTxnId_whenColumnBlank() {
        String csv = String.join("\n", HEADER, row("2026-10-02 10:00:00", "", "pay-001", "1.00"));

        List<ChannelBillItem> items = WechatBillCsvParser.parse(csv);

        assertEquals(1, items.size());
        assertNull(items.get(0).channelTxnId());
    }
}
