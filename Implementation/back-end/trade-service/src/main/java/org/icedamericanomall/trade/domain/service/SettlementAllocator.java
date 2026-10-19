package org.icedamericanomall.trade.domain.service;

import org.icedamericanomall.trade.domain.model.finance.SettlementRemaining;

import java.util.ArrayList;
import java.util.List;

/**
 * 核销规划（纯函数，无 Spring/无 DB）：把一笔提现金额按 <b>FIFO</b> 摊到若干结算单上。
 *
 * <p><b>为什么是 FIFO</b>：账期先到先核销，规则可解释、可复算，且不引入「用户指定单据」的交互
 * （那是另一批需求）。顺序由调用方的查询保证（{@code period_end ASC, id ASC}），本类不排序 —— 
 * 排序是 SQL 的职责，这里再排一次会掩盖「查询忘了排序」的错。
 *
 * <p><b>返回值可能「不够」</b>：候选合计小于 {@code total} 时，这里<b>不抛异常</b>，只返回能摊的部分。
 * 由调用方校验 {@code sum(plan) == total} 并<b>失败关闭</b> —— 因为「够不够」是数据一致性断言，
 * 不是规划算法的事；纯函数保持无异常便于单测穷举。
 */
public final class SettlementAllocator {

    private SettlementAllocator() {
    }

    /**
     * 一条核销明细：从哪张单、核销多少。
     *
     * @param settlementId 结算单技术主键
     * @param amount       核销金额（分），恒 &gt; 0
     */
    public record Allocation(Long settlementId, int amount) {
    }

    /**
     * 按候选顺序 FIFO 摊分。
     *
     * @param total      待核销总额（分）；&le; 0 返回空计划
     * @param candidates 候选结算单（按账期升序排列，调用方负责）
     * @return 明细列表；可能为空，也可能合计小于 {@code total}（调用方必须校验）
     */
    public static List<Allocation> fifo(int total, List<SettlementRemaining> candidates) {
        if (total <= 0 || candidates == null) {
            return List.of();
        }
        List<Allocation> plan = new ArrayList<>();
        int left = total;
        for (SettlementRemaining candidate : candidates) {
            if (left == 0) {
                break;
            }
            int take = Math.min(left, candidate.remaining());
            if (take <= 0) {
                continue;
            }
            plan.add(new Allocation(candidate.settlementId(), take));
            left -= take;
        }
        return plan;
    }

    /** 计划合计（分）。 */
    public static int sum(List<Allocation> plan) {
        int total = 0;
        for (Allocation allocation : plan) {
            total += allocation.amount();
        }
        return total;
    }
}
