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
 * 商家资金账户 PO —— {@code seller_fund_account} 表（一格一商家）。
 *
 * <p>字段语义见领域模型 {@link org.icedamericanomall.trade.domain.model.finance.SellerFundAccount}；
 * PO 只做物理映射，不含任何计算（可提现额的口径只有领域层一处）。
 */
@Data
@TableName("seller_fund_account")
public class SellerFundAccountPO implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sellerId;
    /** 累计已结算（分）。 */
    private Integer settledTotal;
    /** 待审提现冻结额（分）；第一步恒 0。 */
    private Integer frozenAmount;
    /** 累计已打款（分）。 */
    private Integer paidTotal;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
