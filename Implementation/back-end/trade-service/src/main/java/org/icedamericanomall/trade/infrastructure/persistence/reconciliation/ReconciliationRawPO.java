package org.icedamericanomall.trade.infrastructure.persistence.reconciliation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 渠道原始报文分片（表 reconciliation_raw）。
 *
 * <p>入库而非落本地盘：容器化下本地盘不可靠、也不能跨实例读。分片规则见台账 §1.54③。
 */
@Data
@TableName("reconciliation_raw")
public class ReconciliationRawPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchNo;

    /** 分片序号，从 1 递增；与 batchNo 组成唯一键。 */
    private Integer seq;

    /** 原始报文（可压缩）；按行数分片，超长单条单独成片。 */
    private byte[] payload;

    private LocalDateTime createTime;
}
