package org.icedamericanomall.trade.app.finance;

import org.icedamericanomall.trade.domain.repository.SettlementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 触发入口「批处理外壳」的单测 —— 验的是<b>循环语义与四段计数</b>，不是结算语义
 * （结算本身由 {@code SettlementFundAccountH2Test} 用真库证）。
 *
 * <p>为什么必须区分 {@code skipped} 与 {@code failed}：只返回一个数字时，
 * 「今天没有可结算的」与「有单但全失败」看起来一模一样，运维无法判断该不该报警。
 *
 * <p>另一条：候选查询与逐条结算必须用<b>同一对</b> {@code today}/{@code now}
 * （跨零点扫描不会一半按 D 判、一半按 D+1 判）。
 */
@ExtendWith(MockitoExtension.class)
class SettlementSweeperTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 23, 59);
    private static final LocalDate TODAY = NOW.toLocalDate();
    private static final int LIMIT = 50;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private SettlementManager settlementManager;

    @InjectMocks
    private SettlementSweeper sweeper;

    @Test
    @DisplayName("扫描 — 全部命中：每条候选都结算一次，四段计数齐全且健康")
    void shouldSettleEveryCandidate_whenAllSucceed() {
        when(settlementRepository.findSettleableIds(TODAY, LIMIT, null)).thenReturn(List.of(11L, 12L, 13L));
        when(settlementManager.settleAt(11L, TODAY, NOW)).thenReturn(true);
        when(settlementManager.settleAt(12L, TODAY, NOW)).thenReturn(true);
        when(settlementManager.settleAt(13L, TODAY, NOW)).thenReturn(true);

        SettlementSweeper.SweepResult result = sweeper.sweep(TODAY, NOW, LIMIT);

        assertEquals(3, result.scanned());
        assertEquals(3, result.settled());
        assertEquals(0, result.skipped());
        assertEquals(0, result.failed());
        assertTrue(result.healthy());
    }

    @Test
    @DisplayName("扫描 — 条件不满足（返回 false）计入 skipped，**不算 failed**（运维不该为此报警）")
    void shouldCountSkipped_whenConditionMisses() {
        when(settlementRepository.findSettleableIds(TODAY, LIMIT, null)).thenReturn(List.of(11L, 12L));
        when(settlementManager.settleAt(11L, TODAY, NOW)).thenReturn(true);
        when(settlementManager.settleAt(12L, TODAY, NOW)).thenReturn(false);

        SettlementSweeper.SweepResult result = sweeper.sweep(TODAY, NOW, LIMIT);

        assertEquals(2, result.scanned());
        assertEquals(1, result.settled());
        assertEquals(1, result.skipped());
        assertEquals(0, result.failed());
        assertTrue(result.healthy(), "条件不满足是正常现象，不是异常失败");
    }

    @Test
    @DisplayName("扫描 — 某条抛异常计入 failed、只跳过该条，整批继续（异常不冒泡）")
    void shouldCountFailedAndKeepGoing_whenOneCandidateThrows() {
        when(settlementRepository.findSettleableIds(TODAY, LIMIT, null)).thenReturn(List.of(11L, 12L, 13L));
        when(settlementManager.settleAt(11L, TODAY, NOW)).thenReturn(true);
        when(settlementManager.settleAt(12L, TODAY, NOW)).thenThrow(new IllegalStateException("模拟单条结算失败"));
        when(settlementManager.settleAt(13L, TODAY, NOW)).thenReturn(true);

        SettlementSweeper.SweepResult result = sweeper.sweep(TODAY, NOW, LIMIT);

        assertEquals(3, result.scanned());
        assertEquals(2, result.settled());
        assertEquals(0, result.skipped());
        assertEquals(1, result.failed());
        assertFalse(result.healthy(), "有异常失败 ⇒ 不健康，值得报警");
        verify(settlementManager).settleAt(13L, TODAY, NOW);
    }

    @Test
    @DisplayName("扫描 — 补跑某账期：periodEnd 透传给候选查询（不是内存过滤）")
    void shouldPassPeriodEndToQuery() {
        LocalDate periodEnd = LocalDate.of(2026, 10, 18);
        when(settlementRepository.findSettleableIds(TODAY, LIMIT, periodEnd)).thenReturn(List.of());

        sweeper.sweep(TODAY, NOW, LIMIT, periodEnd);

        verify(settlementRepository).findSettleableIds(TODAY, LIMIT, periodEnd);
    }

    @Test
    @DisplayName("扫描 — 失败明细带结算单 id 与原因（运维要能当场定位，不用翻日志）")
    void shouldCollectFailureDetails() {
        when(settlementRepository.findSettleableIds(TODAY, LIMIT, null)).thenReturn(List.of(12L));
        when(settlementManager.settleAt(12L, TODAY, NOW))
                .thenThrow(new IllegalStateException("模拟单条结算失败"));

        SettlementSweeper.SweepResult result = sweeper.sweep(TODAY, NOW, LIMIT);

        assertEquals(1, result.failures().size());
        SettlementSweeper.Failure failure = result.failures().get(0);
        assertEquals(12L, failure.settlementId());
        assertNotNull(failure.reason());
        assertTrue(failure.reason().contains("模拟单条结算失败"), "原因必须原样带上，截断的是条数不是信息");
    }

    @Test
    @DisplayName("扫描 — 没有候选：一条都不调，全 0（不空转、不报错）")
    void shouldDoNothing_whenNoCandidate() {
        when(settlementRepository.findSettleableIds(TODAY, LIMIT, null)).thenReturn(List.of());

        SettlementSweeper.SweepResult result = sweeper.sweep(TODAY, NOW, LIMIT);

        assertEquals(0, result.scanned());
        assertTrue(result.healthy());
        verify(settlementManager, never()).settleAt(anyLong(), any(), any());
    }

    @Test
    @DisplayName("扫描 — limit <= 0 直接全 0，连候选都不查（防传错值扫全表）")
    void shouldNotQuery_whenLimitNotPositive() {
        assertEquals(0, sweeper.sweep(TODAY, NOW, 0).scanned());
        assertEquals(0, sweeper.sweep(TODAY, NOW, -5).scanned());

        verify(settlementRepository, never()).findSettleableIds(any(), anyInt(), any());
    }
}
