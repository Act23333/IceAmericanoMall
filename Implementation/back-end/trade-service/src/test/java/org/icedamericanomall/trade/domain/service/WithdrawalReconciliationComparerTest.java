package org.icedamericanomall.trade.domain.service;

import org.icedamericanomall.trade.domain.enums.ReconciliationStatus;
import org.icedamericanomall.trade.domain.service.WithdrawalReconciliationComparer.Channel;
import org.icedamericanomall.trade.domain.service.WithdrawalReconciliationComparer.DiffType;
import org.icedamericanomall.trade.domain.service.WithdrawalReconciliationComparer.Item;
import org.icedamericanomall.trade.domain.service.WithdrawalReconciliationComparer.MatchType;
import org.icedamericanomall.trade.domain.service.WithdrawalReconciliationComparer.Ours;
import org.icedamericanomall.trade.domain.service.WithdrawalReconciliationComparer.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 提现对账第一批：比较器匹配次序 + 批次状态机迁移/重入。
 *
 * <p>全部纯函数与枚举，无 IO、无 Spring、不依赖渠道。
 */
@DisplayName("提现对账（第一批）：比较器 + 批次状态机")
class WithdrawalReconciliationComparerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 18);

    @Test
    @DisplayName("幂等键一致且金额相同 ⇒ EXACT / 无差异")
    void shouldMatchExactly_whenPayoutNoAndAmountAgree() {
        Result result = exactly(4_000);

        assertEquals(1, result.matched());
        assertEquals(0, result.diff());
        assertEquals(MatchType.EXACT, result.items().get(0).matchType());
        assertEquals(DiffType.NONE, result.items().get(0).diffType());
    }

    @Test
    @DisplayName("幂等键一致但金额不同 ⇒ AMOUNT_MISMATCH（不能算匹配）")
    void shouldFlagAmountMismatch_whenPayoutNoAgreesButAmountDiffers() {
        Result result = exactly(3_999);

        assertEquals(0, result.matched());
        assertEquals(1, result.diff());
        assertEquals(DiffType.AMOUNT_MISMATCH, result.items().get(0).diffType());
    }

    @Test
    @DisplayName("渠道号缺失但金额相同、账期差一天 ⇒ FUZZY")
    void shouldMatchFuzzy_whenAmountAgreesWithinOneDay() {
        Result result = WithdrawalReconciliationComparer.compare(
                List.of(new Ours("WD-1", 4_000, DAY)),
                List.of(new Channel("CH-9", null, 4_000, DAY.plusDays(1))));

        assertEquals(1, result.matched());
        assertEquals(MatchType.FUZZY, result.items().get(0).matchType());
    }

    @Test
    @DisplayName("账期差超过一天 ⇒ 归差异（OURS_ONLY + CHANNEL_ONLY）")
    void shouldReportDiff_whenDatesAreFarApart() {
        Result result = WithdrawalReconciliationComparer.compare(
                List.of(new Ours("WD-1", 4_000, DAY)),
                List.of(new Channel("CH-9", null, 4_000, DAY.plusDays(2))));

        assertEquals(0, result.matched());
        assertEquals(2, result.diff());
        assertTrue(result.items().stream().anyMatch(i -> i.diffType() == DiffType.OURS_ONLY));
        assertTrue(result.items().stream().anyMatch(i -> i.diffType() == DiffType.CHANNEL_ONLY));
    }

    @Test
    @DisplayName("渠道多出一笔 ⇒ CHANNEL_ONLY，且不吞掉我方条目")
    void shouldReportChannelOnly() {
        Result result = WithdrawalReconciliationComparer.compare(
                List.of(new Ours("WD-1", 4_000, DAY)),
                List.of(new Channel("CH-1", "WD-1", 4_000, DAY),
                        new Channel("CH-2", "WD-X", 5_000, DAY)));

        assertEquals(1, result.matched());
        assertEquals(1, result.diff());
        Item extra = result.items().stream()
                .filter(i -> i.diffType() != DiffType.NONE)
                .findFirst()
                .orElseThrow();
        assertEquals(DiffType.CHANNEL_ONLY, extra.diffType());
    }

    @Test
    @DisplayName("两边都空 ⇒ 空结果（不抛异常）")
    void shouldHandleEmptyInputs() {
        Result result = WithdrawalReconciliationComparer.compare(List.of(), List.of());

        assertEquals(0, result.matched());
        assertEquals(0, result.diff());
        assertTrue(result.items().isEmpty());
    }

    @Test
    @DisplayName("状态机：合法推进（PENDING→FETCHED→MATCHED/DIFF→RESOLVED）")
    void shouldAllowForwardTransitions() {
        assertTrue(ReconciliationStatus.canTransit(ReconciliationStatus.PENDING, ReconciliationStatus.FETCHED));
        assertTrue(ReconciliationStatus.canTransit(ReconciliationStatus.FETCHED, ReconciliationStatus.MATCHED));
        assertTrue(ReconciliationStatus.canTransit(ReconciliationStatus.FETCHED, ReconciliationStatus.DIFF));
        assertTrue(ReconciliationStatus.canTransit(ReconciliationStatus.DIFF, ReconciliationStatus.RESOLVED));
        assertTrue(ReconciliationStatus.canTransit(ReconciliationStatus.MATCHED, ReconciliationStatus.RESOLVED));
    }

    @Test
    @DisplayName("状态机：跳步/回退/RESOLVED 再变 ⇒ 一律拒绝")
    void shouldRejectIllegalTransitions() {
        assertFalse(ReconciliationStatus.canTransit(ReconciliationStatus.PENDING, ReconciliationStatus.MATCHED));
        assertFalse(ReconciliationStatus.canTransit(ReconciliationStatus.MATCHED, ReconciliationStatus.DIFF));
        assertFalse(ReconciliationStatus.canTransit(ReconciliationStatus.RESOLVED, ReconciliationStatus.DIFF));
        assertFalse(ReconciliationStatus.canTransit(null, ReconciliationStatus.FETCHED));
    }

    @Test
    @DisplayName("重入语义：PENDING/FETCHED 可重拉；闭环后必须新建批次")
    void shouldOnlyAllowReentryBeforeClosure() {
        assertTrue(ReconciliationStatus.PENDING.reentrant());
        assertTrue(ReconciliationStatus.FETCHED.reentrant());
        assertFalse(ReconciliationStatus.MATCHED.reentrant());
        assertFalse(ReconciliationStatus.DIFF.reentrant());
        assertFalse(ReconciliationStatus.RESOLVED.reentrant());
    }

    private Result exactly(int channelAmount) {
        return WithdrawalReconciliationComparer.compare(
                List.of(new Ours("WD-1", 4_000, DAY)),
                List.of(new Channel("CH-1", "WD-1", channelAmount, DAY)));
    }
}
