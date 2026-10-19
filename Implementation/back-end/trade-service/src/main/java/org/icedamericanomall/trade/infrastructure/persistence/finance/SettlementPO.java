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
    /** 已核销提现金额（分）。基础字段只读；写入只走核销仓储的条件更新，不做 setter 赋值。 */
    private Integer withdrawnAmount;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
