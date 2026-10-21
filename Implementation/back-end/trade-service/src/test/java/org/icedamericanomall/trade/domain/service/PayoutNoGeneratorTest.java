package org.icedamericanomall.trade.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 出款单号生成器用例。
 *
 * <p>重点是**长度**：单号进数据库列、也进渠道报文，长度超了不是"不好看"，是写不进去/被渠道拒。
 * 因此这里直接拿 `Integer.MAX_VALUE` 当序号测上界 —— 不拿小数字证明"够用"。
 */
@DisplayName("出款单号生成")
class PayoutNoGeneratorTest {

    private static final String WITHDRAWAL_NO = "9f3ac1b27e0d4a56bc0d1e2f3a4b5c6d";

    @Test
    @DisplayName("序号取到 Integer.MAX_VALUE 时长度仍在上限内（极端值证明，不用小数字）")
    void shouldStayWithinMaxLength_whenAttemptNoIsIntegerMaxValue() {
        String payoutNo = PayoutNoGenerator.generate(WITHDRAWAL_NO, Integer.MAX_VALUE);

        assertTrue(payoutNo.length() <= PayoutNoGenerator.MAX_LENGTH,
                "单号长度必须 ≤ " + PayoutNoGenerator.MAX_LENGTH + "，实际 " + payoutNo.length());
        // 极端值下仍留余量：16 位短哈希 + 连字符 + 十位序号
        assertEquals(16 + 1 + String.valueOf(Integer.MAX_VALUE).length(), payoutNo.length(),
                "长度构成必须可解释：短哈希 + 连字符 + 序号位数");
    }

    @Test
    @DisplayName("同一提现单的不同尝试得到不同单号（换号即新尝试）")
    void shouldDifferAcrossAttempts() {
        String first = PayoutNoGenerator.generate(WITHDRAWAL_NO, 1);
        String second = PayoutNoGenerator.generate(WITHDRAWAL_NO, 2);

        assertNotEquals(first, second, "终态后重发必须是新单号，不能复用");
        assertTrue(first.endsWith("-1") && second.endsWith("-2"), "序号必须体现在单号里，便于回溯");
    }

    @Test
    @DisplayName("不同提现单的单号前缀不同（短哈希区分提现单）")
    void shouldDifferAcrossWithdrawals() {
        String a = PayoutNoGenerator.generate("aaaa", 1);
        String b = PayoutNoGenerator.generate("bbbb", 1);

        assertNotEquals(a.substring(0, 16), b.substring(0, 16));
    }

    @Test
    @DisplayName("入参非法直接拒绝：空单号 / 非正序号")
    void shouldRejectIllegalArguments() {
        assertThrows(IllegalArgumentException.class, () -> PayoutNoGenerator.generate(null, 1));
        assertThrows(IllegalArgumentException.class, () -> PayoutNoGenerator.generate("  ", 1));
        assertThrows(IllegalArgumentException.class, () -> PayoutNoGenerator.generate(WITHDRAWAL_NO, 0));
        assertThrows(IllegalArgumentException.class, () -> PayoutNoGenerator.generate(WITHDRAWAL_NO, -1));
    }

    @Test
    @DisplayName("短哈希稳定：同一提现单多次生成前缀一致（否则重算会造出两个单号）")
    void shortHashShouldBeStable() {
        String first = PayoutNoGenerator.generate(WITHDRAWAL_NO, 3);
        String again = PayoutNoGenerator.generate(WITHDRAWAL_NO, 3);

        assertEquals(first, again, "同一入参必须得到同一单号，否则幂等键会漂移");
    }
}
