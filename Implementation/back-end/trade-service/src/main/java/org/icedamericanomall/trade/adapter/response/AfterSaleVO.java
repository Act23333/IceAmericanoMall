package org.icedamericanomall.trade.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 售后申请视图对象（买家列表 / 管理员审核列表共用）。
 *
 * <p>V5.3 逐字段核对结论：<b>无字段可修剪</b> ——
 * {@code id} 必留（管理员审核端点 {@code PUT /api/admin/after-sale/{id}/review} 以它定位）；
 * {@code userId} 必留（管理员据此识别申请人）；其余为业务字段或列表展示所需的时间字段；
 * 审计字段 {@code updateTime} 从未暴露。
 */
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
