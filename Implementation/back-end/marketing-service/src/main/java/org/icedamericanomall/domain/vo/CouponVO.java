package org.icedamericanomall.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class CouponVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String couponId;
    private String name;
    private Integer type;
    private Integer value;
    private Integer minAmount;
    private Long sellerId;
    private Integer totalQty;
    private Integer issuedQty;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
}
