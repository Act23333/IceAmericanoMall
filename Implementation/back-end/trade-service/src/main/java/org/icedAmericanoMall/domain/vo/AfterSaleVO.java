package org.icedAmericanoMall.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 售后申请视图对象。 */
@Data
public class AfterSaleVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderNo;
    private Long userId;
    private Integer type;
    private String reason;
    private Integer status;
    private String adminRemark;
    private Integer refundAmount;
    private String logisticsNumber;
    private LocalDateTime createTime;
}
