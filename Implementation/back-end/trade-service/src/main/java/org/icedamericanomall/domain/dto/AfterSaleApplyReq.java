package org.icedamericanomall.domain.dto;

import lombok.Data;

import java.io.Serializable;

/** 售后申请请求。userId 由登录态注入。 */
@Data
public class AfterSaleApplyReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private String orderNo;
    private Integer type;
    private String reason;
    private Integer refundAmount;
    private String logisticsNumber;
}
