package org.icedamericanomall.trade.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 结算单视图对象（商家财务 / 管理后台共用）。
 *
 * <p>V5.3 逐字段核对结论：<b>无字段可修剪</b> ——
 * {@code id} 必留（商家结算单详情端点 {@code GET /api/seller/finance/settlement/{id}} 以它定位）；
 * {@code sellerId} 必留（管理后台按商家检索）；金额三件套（totalAmount/commission/settlementAmount）
 * 为分单位业务金额；{@code periodStart/periodEnd/orderCount/createTime} 为列表展示所需；
 * 审计字段 {@code updateTime} 从未暴露。
 */
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
