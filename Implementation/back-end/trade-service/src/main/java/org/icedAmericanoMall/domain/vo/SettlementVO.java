package org.icedAmericanoMall.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 结算单视图对象。 */
@Data
public class SettlementVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String settlementNo;
    private Long sellerId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Integer orderCount;
    private Integer totalAmount;
    private Integer commission;
    private Integer settlementAmount;
    private Integer status;
    private LocalDateTime createTime;
}
