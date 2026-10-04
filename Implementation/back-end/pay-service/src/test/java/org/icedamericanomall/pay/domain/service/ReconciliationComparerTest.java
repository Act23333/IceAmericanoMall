package org.icedamericanomall.pay.domain.service;

import org.icedamericanomall.pay.domain.enums.ReconciliationResultEnum;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.domain.model.reconciliation.ReconciliationDetail;
import org.icedamericanomall.pay.domain.service.ReconciliationComparer.BillEntry;
import org.icedamericanomall.pay.domain.service.ReconciliationComparer.Diff;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ReconciliationComparer 对账比对纯函数单元测试 —— 领域层不依赖任何框架，直接静态调用。
 *
 * <p>锁死四态判定口径（1-匹配 / 2-渠道有本地无 / 3-本地有渠道无 / 4-金额不一致）
 * 与两侧总额计算（{@code null} 金额按 0 计，明细保留 {@code null} 供核对）。
 */
@DisplayName("ReconciliationComparer 对账比对纯函数")
class ReconciliationComparerTest {

    private PayOrder localPaid(String payOrderNo, int amount) {
        PayOrder payOrder = new PayOrder();
        payOrder.setPayOrderNo(payOrderNo);
        payOrder.setAmount(amount);
        return payOrder;
    }

    @Test
    @DisplayName("四态判定 — 匹配 / 渠道有本地无 / 本地有渠道无 / 金额不一致各归其位")
    void shouldProduceFourStates_whenComparingBill() {
        Diff diff = ReconciliationComparer.compare(
                List.of(localPaid("pay-001", 9900), localPaid("pay-003", 5000), localPaid("pay-004", 3000)),
                List.of(new BillEntry("pay-001", "TXN-1", 9900),
                        new BillEntry("pay-002", "TXN-2", 12800),
                        new BillEntry("pay-003", "TXN-3", 5500)));

        assertEquals(4, diff.details().size(), "3 条本地 + 1 条渠道独有（pay-002）");
        assertEquals(ReconciliationResultEnum.MATCHED.getCode(), resultOf(diff, "pay-001"));
        assertEquals(ReconciliationResultEnum.CHANNEL_ONLY.getCode(), resultOf(diff, "pay-002"));
        assertEquals(ReconciliationResultEnum.AMOUNT_MISMATCH.getCode(), resultOf(diff, "pay-003"));
        assertEquals(ReconciliationResultEnum.LOCAL_ONLY.getCode(), resultOf(diff, "pay-004"));
    }

    @Test
    @DisplayName("金额不一致明细 — 同时回填两侧金额；缺失侧金额为 null")
    void shouldFillBothAmounts_whenAmountMismatch() {
        Diff diff = ReconciliationComparer.compare(
                List.of(localPaid("pay-003", 5000)),
                List.of(new BillEntry("pay-003", "TXN-3", 5500)));

        ReconciliationDetail mismatch = detailOf(diff, "pay-003");
        assertEquals(5500, mismatch.getChannelAmount());
        assertEquals(5000, mismatch.getLocalAmount());

        Diff withLocalOnly = ReconciliationComparer.compare(
                List.of(localPaid("pay-004", 3000)), List.of());
        ReconciliationDetail localOnly = detailOf(withLocalOnly, "pay-004");
        assertNull(localOnly.getChannelAmount(), "本地有渠道无：渠道侧金额为 null");
        assertNull(localOnly.getChannelTxnId(), "渠道缺位时不回填交易号");
    }

    @Test
    @DisplayName("总额计算 — null 金额按 0 计入总额，明细保留 null")
    void shouldTreatNullAmountAsZeroInTotals() {
        Diff diff = ReconciliationComparer.compare(
                List.of(localPaid("pay-001", 9900)),
                List.of(new BillEntry("pay-001", "TXN-1", null),
                        new BillEntry("pay-002", "TXN-2", 6600)));

        assertEquals(6600, diff.channelTotal(), "渠道总额：null 金额按 0 计");
        assertEquals(9900, diff.localTotal());
        assertNull(detailOf(diff, "pay-001").getChannelAmount(), "总额与明细口径分离：明细保留 null");
    }

    @Test
    @DisplayName("空输入 — 双侧为空时产出空明细与 0 总额")
    void shouldReturnEmptyDiff_whenBothSidesEmpty() {
        Diff diff = ReconciliationComparer.compare(List.of(), List.of());

        assertEquals(0, diff.details().size());
        assertEquals(0, diff.channelTotal());
        assertEquals(0, diff.localTotal());
    }

    private Integer resultOf(Diff diff, String payOrderNo) {
        return detailOf(diff, payOrderNo).getResult();
    }

    private ReconciliationDetail detailOf(Diff diff, String payOrderNo) {
        return diff.details().stream()
                .filter(d -> payOrderNo.equals(d.getPayOrderNo()))
                .findFirst()
                .orElseThrow();
    }
}
