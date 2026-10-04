package org.icedamericanomall.wallet.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 钱包账户表映射对象（PO）—— {@code wallet_account} 表（V5.3 迁移自 user.balance 列）。
 * 仅存在于基础设施持久化层，不外泄。
 */
@Data
@TableName("wallet_account")
public class WalletAccountPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 余额（单位：分） */
    private Integer balance;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
