package org.icedamericanomall.trade.infrastructure.persistence.reconciliation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 提现对账逐笔（表 reconciliation_item）。
 *
 * <p>{@code payoutNo} / {@code channelRef} 列均 {@code NOT NULL}：语义上的"空"由
 * {@code ReconciliationSentinels} 的哨兵承载，**不是在 PO 层写 NULL**。
 */
@Data
@TableName("reconciliation_item")
public class ReconciliationItemPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchNo;

    private String payoutNo;

    private String channelRef;

    private Integer oursAmount;

    private Integer channelAmount;

    private LocalDate bizDate;

    /** 1=EXACT 2=FUZZY 3=DIFF。 */
    private Integer matchType;

    /** CHANNEL_ONLY / OURS_ONLY / AMOUNT_MISMATCH；匹配行留空。 */
    private String diffType;

    private LocalDateTime createTime;
}
