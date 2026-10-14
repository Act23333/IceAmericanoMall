package org.icedamericanomall.pay.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对账差异明细实体 —— 持久化模型（{@code reconciliation_detail} 表，F4 渠道对账）。
 *
 * <p>一条明细 = 一个比对键（{@code pay_order_no}）的四态结论，见
 * {@link org.icedamericanomall.pay.domain.enums.ReconciliationResultEnum}。
 */
@Data
@TableName("reconciliation_detail")
public class ReconciliationDetailPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属批次 ID。 */
    private Long batchId;

    /** 本地支付单号（比对键）。 */
    /** 本地支付单号（比对键，pay_order.pay_order_no 复制，BINARY(16) ↔ byte[]，ADR-050） */
    private byte[] payOrderNo;

    /** 渠道交易号（渠道账单有则回填）。 */
    private String channelTxnId;

    /** 渠道账单金额（分；渠道无此单为 {@code null}）。 */
    private Integer channelAmount;

    /** 本地金额（分；本地无此单为 {@code null}）。 */
    private Integer localAmount;

    /** 比对结果：1-匹配 / 2-渠道有本地无 / 3-本地有渠道无 / 4-金额不一致。 */
    private Integer result;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
