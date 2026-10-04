package org.icedamericanomall.integration.payment;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 微信支付 v3 交易账单 CSV 解析器 —— 把渠道账单文本解析为 {@link ChannelBillItem}（F4 渠道对账）。
 *
 * <p><b>账单格式（微信支付 v3「交易账单」，{@code bill_type=SUCCESS}）</b>：UTF-8 CSV，逗号分隔，
 * 首行为列名，随后每行一笔交易；文件末尾是汇总行（首列为空，如 {@code ,,,,总交易单数,...}）。
 * 关键列（0 基下标）：
 * <pre>
 *   0  交易时间      5  微信订单号（渠道交易号）   6  商户订单号（= 本平台 pay_order_no）
 *   9  交易状态      24 订单金额（元，两位小数）
 * </pre>
 * 解析策略保守：只接受「列数 ≥ 25 且下标 0 / 6 均非空」的行 —— 自然跳过表头、汇总行与空行。
 *
 * <p><b>金额单位</b>：渠道账单以「元」计价（如 {@code 128.00}），本项目一律以「分」为金额单位，
 * 故在适配层内部完成 元→分 转换（{@link #yuanToCents(String)}）。
 * <b>刻意不使用 {@code BigDecimal}/{@code double}</b>：金额在代码中只能以整数分流转（硬约束），
 * 这里的字符串定点解析等价于「移动小数点两位」且无浮点误差。
 */
@Slf4j
final class WechatBillCsvParser {

    /** 微信交易账单的关键列下标（0 基）。 */
    private static final int COL_TRADE_TIME = 0;
    private static final int COL_TRANSACTION_ID = 5;
    private static final int COL_OUT_TRADE_NO = 6;
    private static final int COL_ORDER_AMOUNT = 24;

    /** 有效数据行的最小列数（含下标 24 的订单金额列）。 */
    private static final int MIN_COLUMNS = 25;

    /** 每 1 元对应的分数。 */
    private static final int CENTS_PER_YUAN = 100;

    /** 「yyyy-MM-dd」前缀的最短长度（用于识别交易时间列）。 */
    private static final int DATE_PREFIX_LENGTH = 5;

    /** 元金额的小数位数。 */
    private static final int AMOUNT_SCALE = 2;
    private static final String ZERO_PADDING = "00";

    private WechatBillCsvParser() {
    }

    /**
     * 解析账单 CSV 文本。
     *
     * @param csv 账单文本（可含 UTF-8 BOM；null/空白 → 空列表）
     * @return 账单条目（保持账单行序）
     */
    static List<ChannelBillItem> parse(String csv) {
        List<ChannelBillItem> items = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return items;
        }
        String[] lines = csv.replace("\uFEFF", "").split("\\r?\\n");
        for (String line : lines) {
            ChannelBillItem item = parseLine(line);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    /** 解析单行；非数据行（表头 / 汇总 / 列数不足 / 非交易时间 / 缺商户订单号）返回 {@code null}。 */
    private static ChannelBillItem parseLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] columns = line.split(",", -1);
        if (columns.length < MIN_COLUMNS) {
            return null;
        }
        String outTradeNo = unquote(columns[COL_OUT_TRADE_NO]);
        String tradeTime = unquote(columns[COL_TRADE_TIME]);
        // 表头行的首列是列名（"交易时间"），汇总行首列为空 —— 以「首列是否形如日期」区分数据行
        if (outTradeNo.isEmpty() || !looksLikeDate(tradeTime)) {
            return null;
        }
        return new ChannelBillItem(outTradeNo, blankToNull(unquote(columns[COL_TRANSACTION_ID])),
                yuanToCents(columns[COL_ORDER_AMOUNT]));
    }

    /** 首列是否为 {@code yyyy-MM-dd ...} 形态的交易时间（用于跳过表头/汇总行）。 */
    private static boolean looksLikeDate(String value) {
        if (value == null || value.length() < DATE_PREFIX_LENGTH) {
            return false;
        }
        return Character.isDigit(value.charAt(0))
                && Character.isDigit(value.charAt(1))
                && Character.isDigit(value.charAt(2))
                && Character.isDigit(value.charAt(3))
                && value.charAt(4) == '-';
    }

    /**
     * 元 → 分（定点字符串运算，无浮点误差）。
     *
     * <p>口径：截断到 2 位小数（渠道账单本身即为 2 位小数，超出部分不会出现）；
     * 空/空白 → 0；非法数字 → 0 并告警（对账比对以金额不等体现，不因单行脏数据中断整批解析）。
     *
     * @param yuan 元金额文本（如 {@code 128.00}、{@code 128}、{@code 128.5}）
     * @return 金额（分）
     */
    static int yuanToCents(String yuan) {
        String value = unquote(yuan);
        if (value.isEmpty()) {
            return 0;
        }
        int dot = value.indexOf('.');
        String intPart = dot < 0 ? value : value.substring(0, dot);
        String fracPart = dot < 0 ? "" : value.substring(dot + 1);
        String fraction = (fracPart + ZERO_PADDING).substring(0, AMOUNT_SCALE);
        try {
            return Integer.parseInt(intPart.isEmpty() ? "0" : intPart) * CENTS_PER_YUAN
                    + Integer.parseInt(fraction);
        } catch (NumberFormatException e) {
            log.warn("账单金额列非法，按 0 处理: value={}", yuan);
            return 0;
        }
    }

    /** 去掉可能存在的 CSV 引号与首尾空白。 */
    private static String unquote(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
