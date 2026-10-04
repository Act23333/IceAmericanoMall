package org.icedamericanomall.pay.adapter.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对账差异明细视图对象（F4）—— AD45 返回。契约见 {@code 05-API-Specification.md} §4.13.14。
 */
@Data
public class ReconciliationDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 明细技术主键。 */
    private Long id;

    /** 所属批次 ID。 */
    private Long batchId;

    /** 本地支付单号（比对键）。 */
    private String payOrderNo;

    /** 渠道交易号（渠道账单有则回填）。 */
    private String channelTxnId;

    /** 渠道账单金额（分；渠道无此单为 {@code null}）。 */
    private Integer channelAmount;

    /** 本地金额（分；本地无此单为 {@code null}）。 */
    private Integer localAmount;

    /** 比对结果：1-匹配 / 2-渠道有本地无 / 3-本地有渠道无 / 4-金额不一致。 */
    private Integer result;

    /** 创建时间。 */
    private LocalDateTime createTime;
}
