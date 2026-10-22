package org.icedamericanomall.trade.infrastructure.persistence.finance;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 出款尝试账 PO（一提现多尝试）。 */
@Data
@TableName("withdrawal_payout_attempt")
public class WithdrawalPayoutAttemptPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long withdrawalId;
    private Integer attemptNo;
    private String payoutNo;
    private String channel;
    private Integer state;
    private String channelRef;
    private String channelMessage;
    private LocalDateTime requestedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
