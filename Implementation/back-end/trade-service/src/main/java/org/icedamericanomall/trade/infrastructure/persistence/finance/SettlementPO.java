package org.icedamericanomall.trade.infrastructure.persistence.finance;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("settlement")
public class SettlementPO implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 结算单号（业务唯一键，BINARY(16) ↔ byte[]；hex 形态仅在领域层，ADR-050） */
    private byte[] settlementNo;
    private Long sellerId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Integer orderCount;
    private Integer totalAmount;
    private Integer commission;
    private Integer settlementAmount;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
