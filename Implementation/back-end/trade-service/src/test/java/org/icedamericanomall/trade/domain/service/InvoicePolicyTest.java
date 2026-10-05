package org.icedamericanomall.trade.domain.service;

import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.trade.domain.enums.InvoiceStatusEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发票域策略纯函数单元测试（F14：金额口径 D3 / 时间窗 D6-O1 / 催开 O10）。
 */
@DisplayName("InvoicePolicy — 金额口径 / 90 天窗 / 催开限频纯函数")
class InvoicePolicyTest {

    private static final LocalDateTime END_TIME = LocalDateTime.of(2026, 10, 1, 12, 0);

    @Test
    @DisplayName("金额口径 — 重算实付与 payAmount 一致 → 返回 payAmount 快照（含运费扣优惠）")
    void shouldReturnPayAmount_whenInvariantHolds() {
        // PRD §15.5.1 场景：12000 − 330 + 880 = 12550
        int amount = InvoicePolicy.resolveInvoiceAmount(12000, 330, 880, 12550);
        assertEquals(12550, amount);
    }

    @Test
    @DisplayName("金额口径 — 三要素与实付不一致（金额被破坏）→ 拒绝开票防虚开")
    void shouldReject_whenAmountInvariantBroken() {
        assertThrows(BizException.class,
                () -> InvoicePolicy.resolveInvoiceAmount(12000, 330, 880, 12000));
    }

    @Test
    @DisplayName("时间窗 — end_time 起 90 天内可申请；恰满 90 天边界含当日；超窗/空 end_time 拒绝")
    void shouldJudgeApplyWindow() {
        assertTrue(InvoicePolicy.withinApplyWindow(END_TIME, END_TIME.plusDays(89)),
                "窗口内可申请");
        assertTrue(InvoicePolicy.withinApplyWindow(END_TIME, END_TIME.plusDays(90)),
                "90 天边界当日含窗内");
        assertFalse(InvoicePolicy.withinApplyWindow(END_TIME, END_TIME.plusDays(90).plusSeconds(1)),
                "超窗拒绝（管理端仍可补开）");
        assertFalse(InvoicePolicy.withinApplyWindow(null, END_TIME.plusDays(1)),
                "end_time 缺失（未确认收货）拒绝");
    }

    @Test
    @DisplayName("催开 — 非待开具拒绝；未满 7 天 TOO_EARLY；24h 内重复 RATE_LIMITED；满足条件 ALLOWED")
    void shouldJudgeUrgeVerdict() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 20, 12, 0);
        LocalDateTime createTime = now.minusDays(8);

        assertEquals(InvoicePolicy.UrgeVerdict.NOT_PENDING,
                InvoicePolicy.checkUrge(InvoiceStatusEnum.ISSUED.getCode(), createTime, null, now));

        assertEquals(InvoicePolicy.UrgeVerdict.TOO_EARLY,
                InvoicePolicy.checkUrge(InvoiceStatusEnum.PENDING_ISSUE.getCode(),
                        now.minusDays(InvoicePolicy.URGE_MIN_PENDING_DAYS).plusSeconds(1), null, now));

        LocalDateTime urgedAt = now.minusHours(23);
        assertEquals(InvoicePolicy.UrgeVerdict.RATE_LIMITED,
                InvoicePolicy.checkUrge(InvoiceStatusEnum.PENDING_ISSUE.getCode(), createTime, urgedAt, now),
                "24h 内已催开过 → 限频");

        assertEquals(InvoicePolicy.UrgeVerdict.ALLOWED,
                InvoicePolicy.checkUrge(InvoiceStatusEnum.PENDING_ISSUE.getCode(), createTime, null, now),
                "从未催开且满 7 天 → 允许");
        assertEquals(InvoicePolicy.UrgeVerdict.ALLOWED,
                InvoicePolicy.checkUrge(InvoiceStatusEnum.PENDING_ISSUE.getCode(), createTime,
                        now.minusHours(InvoicePolicy.URGE_INTERVAL_HOURS), now),
                "距上次催开恰满 24h → 允许（边界含）");
    }
}
