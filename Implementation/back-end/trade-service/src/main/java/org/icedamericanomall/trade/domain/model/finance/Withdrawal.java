package org.icedamericanomall.trade.domain.model.finance;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商家提现单聚合（纯 POJO）—— {@code withdrawal} 表。
 *
 * <p>状态（{@link org.icedamericanomall.trade.domain.enums.WithdrawalStatusEnum}）：
 * 1-PENDING_REVIEW 待审核 → 2-PAID_OUT 已打款 / 3-REJECTED 已拒绝。
 * 打款成功时级联将该商家已结算单据置为已打款（由编排层完成）。
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
