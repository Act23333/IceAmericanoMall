package org.icedamericanomall.pay.domain.model.reconciliation;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对账批次（领域模型）—— {@code reconciliation_batch} 表（F4 渠道对账）。
 *
 * <p>{@code uk_date_channel(bill_date, channel)} 是「同日同渠道一批次」的唯一锚点：
 * 重复执行先删后写，并兜底并发重复触发。状态口径见 {@code ReconciliationStatusEnum}。
 */
@Data
public class ReconciliationBatch implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 账单日。 */
    private LocalDate billDate;

    /** 对账渠道：WECHAT / ALIPAY。 */
    private String channel;

    /** 状态：0-处理中 / 1-完成 / 2-失败。 */
    private Integer status;

    /** 渠道账单总额（分）。 */
    private Integer channelTotal;

    /** 本地支付成功总额（分）。 */
    private Integer localTotal;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
