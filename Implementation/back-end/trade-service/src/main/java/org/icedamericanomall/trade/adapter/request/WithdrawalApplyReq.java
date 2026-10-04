package org.icedamericanomall.trade.adapter.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 商家提现申请请求（{@code POST /api/seller/finance/withdrawal}）。
 *
 * <p>sellerId 由登录态（{@code UserContext}）注入，请求体不承载商家身份。
 * 金额 &gt; 0 的约束与领域服务 {@code WithdrawalServiceImpl.applyWithdrawal} 同源；
 * <b>金额上限（≤ 可用余额）依赖结算数据，仍由领域服务校验</b>。
 * 收款账户信息为可选项，由审核环节人工核对。
 */
@Data
public class WithdrawalApplyReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 提现金额（分）：&gt; 0 且不超过可提现余额。 */
    @NotNull(message = "提现金额不能为空")
    @Min(value = 1, message = "提现金额必须大于 0")
    private Integer amount;

    /** 收款银行卡号。 */
    private String bankAccount;

    /** 开户银行。 */
    private String bankName;
}
