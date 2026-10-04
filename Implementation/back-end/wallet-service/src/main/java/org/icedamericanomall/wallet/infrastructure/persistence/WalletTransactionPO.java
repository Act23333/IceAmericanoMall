package org.icedamericanomall.wallet.infrastructure.persistence;

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
 * 钱包流水表映射对象（PO）—— {@code wallet_transaction} 表。
 * 仅存在于基础设施持久化层，不外泄（领域侧消费 {@code WalletTransaction} record）。
 */
@Data
@TableName("wallet_transaction")
public class WalletTransactionPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 流水号（幂等键，业务唯一键）。 */
    private String transactionNo;

    private Long userId;

    /** 类型：1-收入，2-支出。 */
    private Integer type;

    /** 变动金额（分，恒正）。 */
    private Integer amount;

    /** 业务关联号（退款流水 = 退款单号）。 */
    private String bizNo;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
