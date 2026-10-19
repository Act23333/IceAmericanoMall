package org.icedamericanomall.trade.domain.model.finance;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商家提现单聚合（纯 POJO）—— {@code withdrawal} 表。
 *
 * <p><b>本表是「已打款」的唯一事实源</b>：对账等式 = {@code seller_fund_account.paid_total}
 * == Σ {@code amount} WHERE {@code status = PAID_OUT}。结算单（{@code settlement.status}）**不承载打款事实**
 * —— 它的 {@code SETTLED} 只表示「可提现」。
 *
 * <p>状态（{@link org.icedamericanomall.trade.domain.enums.WithdrawalStatusEnum}）：
 * 1-PENDING_REVIEW 待审核 → 2-PAID_OUT 已打款 / 3-REJECTED 已拒绝。
 * 状态迁移由编排层做<b>条件更新</b>（待审核 → 终态，0 行即幂等）；打款成功时在
 * {@code seller_fund_account} 上累加 {@code paid_total}，<b>不回写结算单状态</b>（T-WITHDRAW-2）。
 * {@code bankAccount} 为敏感字段：商家端视图只回脱敏号（安全模型 12.3）。
 */
@Data
public class Withdrawal {

    private Long id;
    private String withdrawalNo;
    private Long sellerId;
    private Integer amount;
    private Integer status;
    private String bankAccount;
    private String bankName;
    private String adminRemark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
