package org.icedamericanomall.trade.infrastructure.persistence.reconciliation;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 提现对账批次（表 reconciliation_batch）。 */
@Data
@TableName("reconciliation_batch")
public class ReconciliationBatchPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 批次号（幂等键；生成器不得产出 '0' —— 它是"有效批次"标记）。 */
    private String batchNo;

    private String provider;

    private LocalDate bizDate;

    /** 1=PENDING 2=FETCHED 3=MATCHED 4=DIFF 5=RESOLVED。 */
    private Integer status;

    private LocalDateTime fetchedAt;

    private Integer matchedCount;

    private Integer diffCount;

    private LocalDateTime resolvedAt;

    private Long resolvedBy;

    private String resolveNote;

    /** NULL = 当前有效批次（唯一键的一部分由生成列承载）。 */
    private String supersededBy;

    /**
     * 生成列（只读）：有效批次为 {@code '0'}，被取代为 {@code superseded_by#batch_no}。
     *
     * <p>永远不参与写入 —— 写它会被数据库拒绝。
     */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private String activeKey;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
