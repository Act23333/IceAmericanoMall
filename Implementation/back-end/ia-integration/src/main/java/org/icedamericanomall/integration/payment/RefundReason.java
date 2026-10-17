package org.icedamericanomall.integration.payment;

import java.util.regex.Pattern;

/**
 * 退款原因摘要（渠道 {@code reason}）的<b>受控文案</b>与消毒规则（ADR-063 §决策 7 表 #5，批次 B7b）。
 *
 * <p><b>为什么不是「把用户原文截一截就发」</b>：{@code reason} 是<b>人读摘要</b>，会随渠道账单、
 * 对账文件、客服工单外泄。把用户自由文本（可能含手机号、单号、纠纷细节）外送渠道，
 * 等于把内部工单内容写进对外的资金凭证。因此本类提供：
 * <ol>
 *   <li><b>枚举文案优先</b>（{@link #AFTER_SALE} / {@link #ORDER_REFUND} / {@link #GROUP_BUY_FAILED} /
 *       {@link #GENERIC}）—— 稳定、可枚举、永不含标识；</li>
 *   <li><b>自由文本消毒</b>：含任何单号字面量（尤其 32-hex 业务键、20 位渠道号）→ 整句回落
 *       {@link #GENERIC}；否则按 <b>code point</b> 截断到
 *       {@link PaymentClient#MAX_REFUND_REASON_LENGTH}；</li>
 *   <li><b>空值保持空</b>：渠道 {@code reason} 可空，不为了「有个值」编造文案。</li>
 * </ol>
 *
 * <p><b>禁止事项（硬约束）</b>：{@code reason} 里不得出现任何单号 —— {@code order_no} /
 * {@code pay_order_no} / {@code refund_no} / {@code out_refund_no} / {@code channel_trade_no}。
 * 被截断的半截单号比没有单号更误导（客服会拿它去查，查不到就升级工单）。
 */
public enum RefundReason {

    /** 售后退款（买家申请售后，商家/平台审核通过后触发）。 */
    AFTER_SALE("售后退款"),

    /** 订单级退款（无售后单，客服/管理端直接退款）。 */
    ORDER_REFUND("订单退款"),

    /** 拼团失败退款（未成团自动退）。 */
    GROUP_BUY_FAILED("拼团失败退款"),

    /** 通用兜底（入参文本不可外送时使用）。 */
    GENERIC("退款");

    /** 业务键（32-hex）与渠道号（20 位 hex）共同形态：连续 20 位以上小写 hex 视为标识。 */
    private static final Pattern IDENTIFIER_RUN = Pattern.compile("[0-9a-f]{20,}");

    /** 枚举文案（恒 ≤ 渠道上限；{@code RefundReasonTest} 有断言守住）。 */
    private final String text;

    RefundReason(String text) {
        this.text = text;
    }

    /** 对外文案（人类可读、不含标识、长度恒定安全）。 */
    public String text() {
        return text;
    }

    /**
     * 自由文本 → 可外送的渠道 {@code reason}（<b>唯一出口</b>，消毒 + 截断）。
     *
     * @param rawReason      入参摘要（客户/管理端输入，可能含敏感信息或单号）
     * @param identityNumbers 本次退款链路上的标识（{@code channel_trade_no} / {@code out_refund_no} …）；
     *                        出现在文本里即视为泄露 → 回落 {@link #GENERIC}
     * @return 可安全外送的摘要；入参为空 → {@code null}（渠道允许不填）
     */
    public static String channelText(String rawReason, String... identityNumbers) {
        if (rawReason == null || rawReason.isBlank()) {
            return null;
        }
        String candidate = rawReason.strip();
        if (leaksIdentifier(candidate, identityNumbers)) {
            return GENERIC.text();
        }
        return truncateByCodePoints(candidate, PaymentClient.MAX_REFUND_REASON_LENGTH);
    }

    /** 文本是否含单号：显式给定的号（大小写不敏感）+ 任意 ≥20 位小写 hex 连续串。 */
    private static boolean leaksIdentifier(String candidate, String... identityNumbers) {
        if (IDENTIFIER_RUN.matcher(candidate).find()) {
            return true;
        }
        if (identityNumbers == null) {
            return false;
        }
        String lower = candidate.toLowerCase();
        for (String identity : identityNumbers) {
            if (identity != null && !identity.isBlank() && lower.contains(identity.strip().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按 <b>code point</b> 截断（不是字节、也不是 char）。
     *
     * <p>用 {@code char} 截断会把代理对（emoji）劈成两半 —— 落库/外发即成乱码或渠道拒单。
     *
     * @param text      原文
     * @param maxLength 上限（code point 数）
     * @return 截断后的文本（未超限则原样）
     */
    static String truncateByCodePoints(String text, int maxLength) {
        if (text.codePointCount(0, text.length()) <= maxLength) {
            return text;
        }
        int end = text.offsetByCodePoints(0, maxLength);
        return text.substring(0, end);
    }
}
