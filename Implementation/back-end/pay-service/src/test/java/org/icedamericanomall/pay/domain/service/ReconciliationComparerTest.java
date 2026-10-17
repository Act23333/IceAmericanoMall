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
 *
 * <p><b>比对键口径</b>：本地 {@code PayOrder.channelTradeNo} ↔ 账单
 * {@code ChannelBillItem.outTradeNo}（对外渠道号）。测试里两者的支付单号刻意<b>不同</b>
 * （{@code pay-00x} vs {@code chn-00x}），从而锁死「按渠道号而非支付单号比对」这条口径 ——
 * 若实现回退成按 {@code payOrderNo} 比对，四态判定会全部塌成 LOCAL_ONLY / CHANNEL_ONLY。
 */
@DisplayName("ReconciliationComparer 对账比对纯函数")
class ReconciliationComparerTest {

    /** 本地支付成功流水：支付单号（内部）与对外渠道号刻意不同。 */
    private PayOrder localPaid(String channelTradeNo, int amount) {
        PayOrder payOrder = new PayOrder();
        payOrder.setPayOrderNo("pay-" + channelTradeNo.substring("chn-".length()));
        payOrder.setChannelTradeNo(channelTradeNo);
        payOrder.setAmount(amount);
        return payOrder;
    }

    @Test
    @DisplayName("四态判定 — 匹配 / 渠道有本地无 / 本地有渠道无 / 金额不一致各归其位（按对外渠道号比对）")
    void shouldProduceFourStates_whenComparingBill() {
        Diff diff = ReconciliationComparer.compare(
                List.of(localPaid("chn-001", 9900), localPaid("chn-003", 5000), localPaid("chn-004", 3000)),
                List.of(new BillEntry("chn-001", "TXN-1", 9900),
                        new BillEntry("chn-002", "TXN-2", 12800),
                        new BillEntry("chn-003", "TXN-3", 5500)));

        assertEquals(4, diff.details().size(), "3 条本地 + 1 条渠道独有（chn-002）");
        assertEquals(ReconciliationResultEnum.MATCHED.getCode(), resultOf(diff, "chn-001"));
        assertEquals(ReconciliationResultEnum.CHANNEL_ONLY.getCode(), resultOf(diff, "chn-002"));
        assertEquals(ReconciliationResultEnum.AMOUNT_MISMATCH.getCode(), resultOf(diff, "chn-003"));
        assertEquals(ReconciliationResultEnum.LOCAL_ONLY.getCode(), resultOf(diff, "chn-004"));
    }

    @Test
    @DisplayName("明细两侧标识 — 命中行同时有支付单号与渠道号；渠道独有行支付单号为 null、仅渠道号可追")
    void shouldFillBothIdentifiers_whenMatched() {
        Diff diff = ReconciliationComparer.compare(
                List.of(localPaid("chn-001", 9900)),
                List.of(new BillEntry("chn-001", "TXN-1", 9900), new BillEntry("chn-002", "TXN-2", 12800)));

        ReconciliationDetail matched = detailOf(diff, "chn-001");
        assertEquals("pay-001", matched.getPayOrderNo(), "命中行回填内部支付单号供定位");
        assertEquals("chn-001", matched.getChannelTradeNo());

        ReconciliationDetail channelOnly = detailOf(diff, "chn-002");
        assertNull(channelOnly.getPayOrderNo(), "渠道有本地无：没有本地支付单，不得伪造单号");
        assertEquals("chn-002", channelOnly.getChannelTradeNo(), "该类差异只凭渠道号可追");
    }

    @Test
    @DisplayName("金额不一致明细 — 同时回填两侧金额；缺失侧金额为 null")
    void shouldFillBothAmounts_whenAmountMismatch() {
        Diff diff = ReconciliationComparer.compare(
                List.of(localPaid("chn-003", 5000)),
                List.of(new BillEntry("chn-003", "TXN-3", 5500)));

        ReconciliationDetail mismatch = detailOf(diff, "chn-003");
        assertEquals(5500, mismatch.getChannelAmount());
        assertEquals(5000, mismatch.getLocalAmount());

        Diff withLocalOnly = ReconciliationComparer.compare(
                List.of(localPaid("chn-004", 3000)), List.of());
        ReconciliationDetail localOnly = detailOf(withLocalOnly, "chn-004");
        assertNull(localOnly.getChannelAmount(), "本地有渠道无：渠道侧金额为 null");
        assertNull(localOnly.getChannelTxnId(), "渠道缺位时不回填交易号");
    }

    @Test
    @DisplayName("总额计算 — null 金额按 0 计入总额，明细保留 null")
    void shouldTreatNullAmountAsZeroInTotals() {
        Diff diff = ReconciliationComparer.compare(
                List.of(localPaid("chn-001", 9900)),
                List.of(new BillEntry("chn-001", "TXN-1", null),
                        new BillEntry("chn-002", "TXN-2", 6600)));

        assertEquals(6600, diff.channelTotal(), "渠道总额：null 金额按 0 计");
        assertEquals(9900, diff.localTotal());
        assertNull(detailOf(diff, "chn-001").getChannelAmount(), "总额与明细口径分离：明细保留 null");
    }

    @Test
    @DisplayName("空输入 — 双侧为空时产出空明细与 0 总额")
    void shouldReturnEmptyDiff_whenBothSidesEmpty() {
        Diff diff = ReconciliationComparer.compare(List.of(), List.of());

        assertEquals(0, diff.details().size());
        assertEquals(0, diff.channelTotal());
        assertEquals(0, diff.localTotal());
    }

    private Integer resultOf(Diff diff, String channelTradeNo) {
        return detailOf(diff, channelTradeNo).getResult();
    }

    private ReconciliationDetail detailOf(Diff diff, String channelTradeNo) {
        return diff.details().stream()
                .filter(d -> channelTradeNo.equals(d.getChannelTradeNo()))
                .findFirst()
                .orElseThrow();
    }
}
