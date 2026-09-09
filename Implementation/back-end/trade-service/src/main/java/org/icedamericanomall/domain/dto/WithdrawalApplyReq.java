package org.icedamericanomall.domain.dto;

import lombok.Data;

import java.io.Serializable;

/** 提现申请请求。sellerId 由登录态注入。 */
@Data
public class WithdrawalApplyReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer amount;
    private String bankAccount;
    private String bankName;
}
