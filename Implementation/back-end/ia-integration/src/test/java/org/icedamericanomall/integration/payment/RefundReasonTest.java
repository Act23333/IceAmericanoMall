package org.icedamericanomall.integration.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 退款原因消毒测试（ADR-063 §决策 7 表 #5，批次 B7b）—— 锁定三件事：
 *
 * <ol>
 *   <li><b>枚举文案恒安全</b>：每个文案长度 ≤ {@link PaymentClient#MAX_REFUND_REASON_LENGTH}，且不含标识；</li>
 *   <li><b>单号守卫</b>：文本含任意单号（32-hex 业务键 / 20 位渠道号 / 显式传入的号）→ 整句回落通用文案，
 *       <b>绝不外发半截单号</b>；</li>
 *   <li><b>按 code point 截断</b>：中文与 emoji 不被劈成半个字符（按字节/按 char 都会）。</li>
 * </ol>
 */
@DisplayName("RefundReason —— 退款原因人读摘要：消毒 / 不塞单号 / code point 截断")
class RefundReasonTest {

    @Test
    @DisplayName("枚举文案 — 全部 ≤ 渠道上限（80，取微信/支付宝较严者）且无标识字符")
    void shouldKeepEnumTextsWithinChannelLimit() {
        for (RefundReason reason : RefundReason.values()) {
            assertNotNull(reason.text());
            assertTrue(reason.text().length() <= PaymentClient.MAX_REFUND_REASON_LENGTH, reason.name());
            assertTrue(reason.text().chars().noneMatch(Character::isDigit),
                    "枚举文案不应含数字（数字是单号最容易外泄的形态）: " + reason.text());
        }
    }

    @Test
    @DisplayName("渠道上限值 — 取较严者 80（微信 reason），不是支付宝的 256")
    void shouldTakeStrictestChannelLimit() {
        assertEquals(80, PaymentClient.MAX_REFUND_REASON_LENGTH);
        // 与同族常量风格一致（渠道协议知识收在适配层）
        assertEquals(32, PaymentClient.MAX_OUT_TRADE_NO_LENGTH);
    }

    @Test
    @DisplayName("自由文本 — 常规摘要原样外发（不含单号、未超长）")
    void shouldKeepPlainText() {
        assertEquals("质量问题", RefundReason.channelText("质量问题"));
        assertEquals("质量问题", RefundReason.channelText("  质量问题  "));
    }

    @Test
    @DisplayName("空值 — 保持 null（渠道 reason 可空，不编造文案）")
    void shouldKeepNull() {
        assertNull(RefundReason.channelText(null));
        assertNull(RefundReason.channelText("   "));
    }

    @Test
    @DisplayName("单号守卫生效①：文本含 32-hex 业务键（pay_order_no / refund_no 形态）→ 回落通用文案")
    void shouldFallBackWhenBusinessKeyPresent() {
        String payOrderNo = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";

        String wire = RefundReason.channelText("退款单 " + payOrderNo + " 质量问题");

        assertEquals(RefundReason.GENERIC.text(), wire);
        assertFalse(wire.contains(payOrderNo), "任何单号都不得外送（含其片段）");
    }

    @Test
    @DisplayName("单号守卫生效②：文本含本次渠道号（out_trade_no / out_refund_no）→ 回落通用文案")
    void shouldFallBackWhenChannelNumberPresent() {
        String channelTradeNo = "a1b2c3d4e5f6a7b8c9d0";
        String outRefundNo = "0f1e2d3c4b5a69788796";

        assertEquals(RefundReason.GENERIC.text(),
                RefundReason.channelText("退款 " + channelTradeNo, channelTradeNo, outRefundNo));
        assertEquals(RefundReason.GENERIC.text(),
                RefundReason.channelText("退款 " + outRefundNo, channelTradeNo, outRefundNo));
    }

    @Test
    @DisplayName("单号守卫生效③：20 位以上小写 hex 连续串即视为标识（形态判据，不依赖调用方传参）")
    void shouldFallBackWhenHexRunPresent() {
        assertEquals(RefundReason.GENERIC.text(),
                RefundReason.channelText("order 0123456789abcdef0123"));
        // 短于 20 位不算标识（避免把正常文案里的英文数字误判）
        assertEquals("订单 0123456789abcd 退款",
                RefundReason.channelText("订单 0123456789abcd 退款"));
    }

    @Test
    @DisplayName("截断按 code point：80 个中文/emoji 保留 80 个字符，且不劈代理对")
    void shouldTruncateByCodePoints() {
        String chinese = "退".repeat(100);
        String truncated = RefundReason.channelText(chinese);
        assertEquals(80, truncated.length());

        // emoji 是代理对：按 char 截断会留下半个字符（长度仍是 80 但内容非法）
        String emojis = "😀".repeat(100);
        String truncatedEmoji = RefundReason.channelText(emojis);
        assertEquals(80, truncatedEmoji.codePointCount(0, truncatedEmoji.length()));
        assertTrue(truncatedEmoji.equals("😀".repeat(80)), "不得劈开代理对");
        assertEquals(160, truncatedEmoji.length(), "80 个 emoji = 160 个 char（代理对）");
    }

    @Test
    @DisplayName("未超长不截断（边界 80 保持原样）")
    void shouldNotTruncateAtBoundary() {
        String exactly80 = "退".repeat(PaymentClient.MAX_REFUND_REASON_LENGTH);
        assertEquals(exactly80, RefundReason.channelText(exactly80));
    }
}
