package org.icedamericanomall.trade.domain.model.finance;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商家结算单聚合（纯 POJO）—— {@code settlement} 表。
 *
 * <p>状态（{@link org.icedamericanomall.trade.domain.enums.SettlementStatusEnum}）：
 * 1-PENDING 待结算 → 2-SETTLED 已结算（可提现）→ 3-PAID_OUT 已打款。
 * 佣金为整数百分比计算（{@code commission = payAmount × 5 / 100}），禁止浮点。
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
