package org.icedamericanomall.trade.domain.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 提现对账比较器（纯函数：无 IO、无 Spring、无时间源）。
 *
 * <p><b>匹配次序（写死）</b>：① 按 {@code payoutNo} 精确匹配；② 剩余项按「金额相同 + 账期相差不超一天」
 * 模糊匹配；③ 仍匹配不到的归差异（{@code OURS_ONLY} / {@code CHANNEL_ONLY}）。
 * 模糊匹配只用于「渠道流水号丢了但钱对得上」的现实情况，<b>不会</b>把金额不同的配成一对。
 *
 * <p><b>E7 口径</b>：本比较器只处理<b>提现类</b>流水（调用方负责过滤），产出的是<b>报表条目</b>而非门禁 ——
 * 差异是业务输入，不是缺陷。
 */
public final class WithdrawalReconciliationComparer {

    /** 模糊匹配允许的最大账期偏差（天）。 */
    private static final int FUZZY_DAY_TOLERANCE = 1;

    private WithdrawalReconciliationComparer() {
    }

    /** 我方一条已打款流水。 */
    public record Ours(String payoutNo, int amount, LocalDate bizDate) {
    }

    /** 渠道一条逐笔流水。 */
    public record Channel(String channelRef, String payoutNo, int amount, LocalDate bizDate) {
    }

    /** 匹配形态。 */
    public enum MatchType {
        /** 幂等键直接对上。 */
        EXACT,
        /** 金额 + 邻近账期对上（渠道号缺失或不一致）。 */
        FUZZY,
        /** 差异。 */
        DIFF
    }

    /** 差异类型；{@code NONE} 表示无差异。 */
    public enum DiffType {
        NONE,
        CHANNEL_ONLY,
        OURS_ONLY,
        AMOUNT_MISMATCH
    }

    /** 一条对账结果。 */
    public record Item(String payoutNo, String channelRef, Integer oursAmount, Integer channelAmount,
                       LocalDate bizDate, MatchType matchType, DiffType diffType) {
    }

    /** 对账结果。 */
    public record Result(List<Item> items, int matched, int diff) {
    }

    /**
     * 比对。
     *
     * @param ours    我方流水
     * @param channel 渠道逐笔
     * @return 匹配与差异混排的结果（落库后可直接出报表）
     */
    public static Result compare(List<Ours> ours, List<Channel> channel) {
        List<Item> items = new ArrayList<>();
        boolean[] used = new boolean[channel.size()];

        Map<String, List<Integer>> byPayoutNo = new HashMap<>();
        for (int i = 0; i < channel.size(); i++) {
            String no = channel.get(i).payoutNo();
            if (no != null && !no.isBlank()) {
                byPayoutNo.computeIfAbsent(no, key -> new ArrayList<>()).add(i);
            }
        }

        List<Ours> pending = new ArrayList<>();
        for (Ours row : ours) {
            Integer hit = firstFree(byPayoutNo.get(row.payoutNo()), used);
            if (hit == null) {
                pending.add(row);
                continue;
            }
            used[hit] = true;
            Channel other = channel.get(hit);
            boolean sameAmount = other.amount() == row.amount();
            items.add(new Item(row.payoutNo(), other.channelRef(), row.amount(), other.amount(),
                    row.bizDate(), MatchType.EXACT,
                    sameAmount ? DiffType.NONE : DiffType.AMOUNT_MISMATCH));
        }

        List<Ours> stillPending = new ArrayList<>();
        for (Ours row : pending) {
            int hit = uniqueFuzzyHit(row, channel, used);
            if (hit < 0) {
                stillPending.add(row);
                continue;
            }
            used[hit] = true;
            Channel other = channel.get(hit);
            items.add(new Item(row.payoutNo(), other.channelRef(), row.amount(), other.amount(),
                    row.bizDate(), MatchType.FUZZY, DiffType.NONE));
        }

        for (Ours row : stillPending) {
            items.add(new Item(row.payoutNo(), null, row.amount(), null, row.bizDate(),
                    MatchType.DIFF, DiffType.OURS_ONLY));
        }
        for (int i = 0; i < channel.size(); i++) {
            if (used[i]) {
                continue;
            }
            Channel other = channel.get(i);
            items.add(new Item(other.payoutNo(), other.channelRef(), null, other.amount(),
                    other.bizDate(), MatchType.DIFF, DiffType.CHANNEL_ONLY));
        }

        int matched = (int) items.stream().filter(item -> item.diffType() == DiffType.NONE).count();
        return new Result(List.copyOf(items), matched, items.size() - matched);
    }

    private static Integer firstFree(List<Integer> candidates, boolean[] used) {
        if (candidates == null) {
            return null;
        }
        for (Integer index : candidates) {
            if (!used[index]) {
                return index;
            }
        }
        return null;
    }

    /**
     * 模糊匹配：<b>只在候选唯一时生效</b>。
     *
     * <p>同一金额有多笔候选时不做猜测 —— 猜错会把差异"抹平"，比留下差异更危险。
     * 候选为多时返回 {@code -1}，调用方按差异处理。
     */
    private static int uniqueFuzzyHit(Ours row, List<Channel> channel, boolean[] used) {
        int found = -1;
        for (int i = 0; i < channel.size(); i++) {
            if (used[i]) {
                continue;
            }
            Channel other = channel.get(i);
            if (other.amount() != row.amount() || other.bizDate() == null || row.bizDate() == null) {
                continue;
            }
            if (Math.abs(ChronoUnit.DAYS.between(row.bizDate(), other.bizDate())) > FUZZY_DAY_TOLERANCE) {
                continue;
            }
            if (found >= 0) {
                return -1;      // 候选不唯一 ⇒ 不猜
            }
            found = i;
        }
        return found;
    }
}
