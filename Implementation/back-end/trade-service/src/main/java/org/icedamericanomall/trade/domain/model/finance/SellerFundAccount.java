package org.icedamericanomall.trade.domain.model.finance;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商家资金账户聚合（纯 POJO）—— {@code seller_fund_account} 表，一格一商家。
 *
 * <p><b>它是「可提现额」的唯一权威口径</b>（T-WITHDRAW 第一步）。此前可提现额 = 「Σ 状态为已结算的结算单金额」，
 * 既不扣已打款也不扣待审提现，于是同一商家可连续申请多笔提现、每笔都能通过校验 —— 这是超额提现的根因。
 *
 * <p><b>口径（写死，别按列名猜）</b>：
 * <pre>
 * 可提现额 = settled_total − frozen_amount − paid_total
 * </pre>
 * <ul>
 *   <li>{@code settled_total} —— <b>累计已结算</b>（只增）。唯一写入者是应用层的结算用例
 *       （{@code SettlementManager#settle}，PENDING→SETTLED 的唯一入口，与状态迁移同事务）；
 *       <b>打款不动它</b>：若打款既减 settled_total 又加 paid_total，同一个金额会被扣两次，
 *       而「Σ 结算单 = settled_total」这条对账等式也会在部分置位时对不上（见 open-items §1.25 的实施口径订正）。</li>
 *   <li>{@code paid_total} —— <b>累计已打款</b>（只增）。</li>
 *   <li>{@code frozen_amount} —— <b>待审提现冻结额</b>。<b>第一步刻意不写它</b>：
 *       冻结的「加」发生在第二步 {@code applyWithdrawal} 预占；先做减法的代价是
 *       减一个从未加过的字段（余额被凭空变小，且与结算单对不上）。</li>
 * </ul>
 *
 * <p><b>不变式</b>：{@link #available()} 不得为负。负值说明「加」与「减」有一边没同事务，
 * 不是可容忍的偏差（同 {@code sku.stock >= 0} 的口径）。
 */
@Data
public class SellerFundAccount {

    private Long id;
    private Long sellerId;
    /** 累计已结算（分）。 */
    private Integer settledTotal;
    /** 待审提现冻结额（分）；第一步恒 0。 */
    private Integer frozenAmount;
    /** 累计已打款（分）。 */
    private Integer paidTotal;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 可提现额（分）= 累计已结算 − 待审冻结 − 累计已打款。 */
    public int available() {
        return nz(settledTotal) - nz(frozenAmount) - nz(paidTotal);
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
