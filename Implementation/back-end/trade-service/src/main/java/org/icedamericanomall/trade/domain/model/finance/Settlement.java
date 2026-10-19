package org.icedamericanomall.trade.domain.model.finance;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商家结算单聚合（纯 POJO）—— {@code settlement} 表。
 *
 * <p>状态（{@link org.icedamericanomall.trade.domain.enums.SettlementStatusEnum}）：
 * 1-PENDING 待结算 → 2-SETTLED <b>已结算（= 可提现，不代表「未打款」）</b> → 3-PAID_OUT 已打款（<b>历史语义</b>）。
 *
 * <p><b>本表不承载打款事实</b>：「谁被打款了」的唯一事实源是 {@code withdrawal}（{@code status = PAID_OUT}），
 * 聚合值看 {@code seller_fund_account.paid_total}。因此不要从 {@code settlement.status} 推断「这笔钱打没打过」，
 * 也不要在打款后回写本表状态（旧的全量置位做法已删除，T-WITHDRAW-2）。
 *
 * <p>编码纪律：佣金为整数百分比计算（{@code commission = payAmount × 5 / 100}），禁止浮点。
 */
@Data
public class Settlement {

    private Long id;
    private String settlementNo;
    private Long sellerId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Integer orderCount;
    private Integer totalAmount;
    private Integer commission;
    private Integer settlementAmount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
