package org.icedamericanomall.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 提现申请视图对象。 */
@Data
public class WithdrawalVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String withdrawalNo;
    private Long sellerId;
    private Integer amount;
    private Integer status;
    private String bankAccount;
    private String bankName;
    private String adminRemark;
    private LocalDateTime createTime;
}
