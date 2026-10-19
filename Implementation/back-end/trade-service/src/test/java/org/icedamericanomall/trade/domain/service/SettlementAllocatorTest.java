package org.icedamericanomall.trade.domain.service;

import org.icedamericanomall.trade.domain.model.finance.SettlementRemaining;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 核销规划纯函数单测。它没有 mock、没有 DB —— 契约就是「按给定顺序吃满，不够就少给」，
 * 「不够」由调用方判定并失败关闭（这也是为什么这里不抛异常）。
 */
class SettlementAllocatorTest {

    @Test
    @DisplayName("刚好吃满一张单")
    void shouldAllocateExactly() {
        List<SettlementAllocator.Allocation> plan = SettlementAllocator.fifo(5_000,
                List.of(new SettlementRemaining(11L, 5_000)));

        assertEquals(List.of(new SettlementAllocator.Allocation(11L, 5_000)), plan);
        assertEquals(5_000, SettlementAllocator.sum(plan));
    }

    @Test
    @DisplayName("跨两张单：先吃满顺序靠前的，余下的落到下一张")
    void shouldSpanTwoSettlements() {
        List<SettlementAllocator.Allocation> plan = SettlementAllocator.fifo(15_000,
                List.of(new SettlementRemaining(11L, 10_000), new SettlementRemaining(12L, 20_000)));

        assertEquals(List.of(new SettlementAllocator.Allocation(11L, 10_000),
                new SettlementAllocator.Allocation(12L, 5_000)), plan);
    }

    @Test
    @DisplayName("跳过余额为 0 的候选（不产生 0 金额明细）")
    void shouldSkipEmptyCandidates() {
        List<SettlementAllocator.Allocation> plan = SettlementAllocator.fifo(1_000,
                List.of(new SettlementRemaining(11L, 0), new SettlementRemaining(12L, 1_000)));

        assertEquals(List.of(new SettlementAllocator.Allocation(12L, 1_000)), plan);
    }

    @Test
    @DisplayName("候选不够：只给能给的，**不抛异常**（够不够由调用方判）")
    void shouldReturnPartial_whenCandidatesInsufficient() {
        List<SettlementAllocator.Allocation> plan = SettlementAllocator.fifo(5_000,
                List.of(new SettlementRemaining(11L, 1_000)));

        assertEquals(1_000, SettlementAllocator.sum(plan), "规划器不补、不抛；调用方必须校验合计");
    }

    @Test
    @DisplayName("边界：金额 ≤ 0 或候选为空 → 空计划")
    void shouldReturnEmpty_onDegenerateInput() {
        assertTrue(SettlementAllocator.fifo(0, List.of(new SettlementRemaining(11L, 100))).isEmpty());
        assertTrue(SettlementAllocator.fifo(-1, List.of(new SettlementRemaining(11L, 100))).isEmpty());
        assertTrue(SettlementAllocator.fifo(100, List.of()).isEmpty());
        assertTrue(SettlementAllocator.fifo(100, null).isEmpty());
    }
}
