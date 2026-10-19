package org.icedamericanomall.trade.app.finance;

import org.icedamericanomall.trade.domain.repository.SettlementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 触发入口「批处理外壳」的单测 —— 验的是<b>循环语义</b>，不是结算语义
 * （结算本身由 {@code SettlementFundAccountH2Test} 用真库证）。
 *
 * <p>四条语义：① 每条候选都走一次 {@code settle}；② 条件未命中（返回 false）不计入；
 * ③ <b>某条抛异常不中断整批</b>（批处理最容易写错的一条：一条坏数据把整批带停）；
 * ④ {@code limit <= 0} 直接 0 条（防止调用方传错把全表扫了）。
 */
@ExtendWith(MockitoExtension.class)
class SettlementSweeperTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    private static final int LIMIT = 50;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private SettlementManager settlementManager;

    @InjectMocks
    private SettlementSweeper sweeper;

    @Test
    @DisplayName("扫描 — 每条候选都结算一次，返回真正结算成功的条数")
    void shouldSettleEveryCandidate_whenAllSucceed() {
        when(settlementRepository.findSettleableIds(TODAY, LIMIT)).thenReturn(List.of(11L, 12L, 13L));
        when(settlementManager.settle(11L)).thenReturn(true);
        when(settlementManager.settle(12L)).thenReturn(true);
        when(settlementManager.settle(13L)).thenReturn(true);

        assertEquals(3, sweeper.sweep(TODAY, LIMIT));

        verify(settlementManager).settle(11L);
        verify(settlementManager).settle(12L);
        verify(settlementManager).settle(13L);
    }

    @Test
    @DisplayName("扫描 — 条件未命中（settle 返回 false）不计入成功数，但仍继续扫后面的")
    void shouldCountOnlyApplied_whenConditionMisses() {
        when(settlementRepository.findSettleableIds(TODAY, LIMIT)).thenReturn(List.of(11L, 12L, 13L));
        when(settlementManager.settle(11L)).thenReturn(true);
        when(settlementManager.settle(12L)).thenReturn(false);
        when(settlementManager.settle(13L)).thenReturn(true);

        assertEquals(2, sweeper.sweep(TODAY, LIMIT));
        verify(settlementManager).settle(13L);
    }

    @Test
    @DisplayName("扫描 — 某条抛异常只跳过该条，整批继续（异常不冒泡）")
    void shouldKeepGoing_whenOneCandidateThrows() {
        when(settlementRepository.findSettleableIds(TODAY, LIMIT)).thenReturn(List.of(11L, 12L, 13L));
        when(settlementManager.settle(11L)).thenReturn(true);
        when(settlementManager.settle(12L)).thenThrow(new IllegalStateException("模拟单条结算失败"));
        when(settlementManager.settle(13L)).thenReturn(true);

        assertEquals(2, sweeper.sweep(TODAY, LIMIT), "坏的那条不算成功，但后面的仍要结算");
        verify(settlementManager).settle(13L);
    }

    @Test
    @DisplayName("扫描 — 没有候选时一条都不调（不空转、不报错）")
    void shouldDoNothing_whenNoCandidate() {
        when(settlementRepository.findSettleableIds(TODAY, LIMIT)).thenReturn(List.of());

        assertEquals(0, sweeper.sweep(TODAY, LIMIT));
        verify(settlementManager, never()).settle(anyLong());
    }

    @Test
    @DisplayName("扫描 — limit <= 0 直接返回 0，连候选都不查（防传错值扫全表）")
    void shouldNotQuery_whenLimitNotPositive() {
        assertEquals(0, sweeper.sweep(TODAY, 0));
        assertEquals(0, sweeper.sweep(TODAY, -5));

        verify(settlementRepository, never()).findSettleableIds(any(), anyInt());
    }
}
