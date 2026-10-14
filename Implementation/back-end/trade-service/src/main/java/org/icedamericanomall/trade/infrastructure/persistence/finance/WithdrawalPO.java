package org.icedamericanomall.trade.infrastructure.persistence.finance;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("withdrawal")
public class WithdrawalPO implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 提现单号（业务唯一键，BINARY(16) ↔ byte[]；hex 形态仅在领域层，ADR-050） */
    private byte[] withdrawalNo;
    private Long sellerId;
    private Integer amount;
    private Integer status;
    private String bankAccount;
    private String bankName;
    private String adminRemark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
