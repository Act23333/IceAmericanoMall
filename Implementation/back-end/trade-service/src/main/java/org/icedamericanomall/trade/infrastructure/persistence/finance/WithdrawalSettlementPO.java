package org.icedamericanomall.trade.infrastructure.persistence.finance;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 提现核销明细 PO：一笔提现「从哪几张结算单、各核销多少」。
 *
 * <p>它是核销的<b>真源</b>；{@code settlement.withdrawn_amount} 是它的聚合投影（靠等式证明没漂）。
 * 拒绝时<b>删行</b>，不留 released 标记 —— 未核销即无分配是硬语义。
 */
@Data
@TableName("withdrawal_settlement")
public class WithdrawalSettlementPO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long withdrawalId;

    private Long settlementId;

    private Integer amount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
