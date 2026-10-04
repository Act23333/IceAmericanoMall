package org.icedamericanomall.pay.domain.model.reconciliation;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对账差异明细（领域模型）—— {@code reconciliation_detail} 表。
 *
 * <p>一条明细 = 一个比对键（{@code pay_order_no}）的四态结论，见
 * {@code ReconciliationResultEnum}（1-匹配 / 2-渠道有本地无 / 3-本地有渠道无 / 4-金额不一致）。
 */
@Data
public class ReconciliationDetail implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 所属批次 ID（由仓储在批量落库时回填）。 */
    private Long batchId;

    /** 本地支付单号（比对键）。 */
    private String payOrderNo;

    /** 渠道交易号（渠道账单有则回填）。 */
    private String channelTxnId;

    /** 渠道账单金额（分；渠道无此单为 {@code null}）。 */
    private Integer channelAmount;

    /** 本地金额（分；本地无此单为 {@code null}）。 */
    private Integer localAmount;

    /** 比对结果（四态 code）。 */
    private Integer result;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
