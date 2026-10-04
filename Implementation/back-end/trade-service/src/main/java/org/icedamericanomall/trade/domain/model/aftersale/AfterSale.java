package org.icedamericanomall.trade.domain.model.aftersale;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 售后单聚合（纯 POJO）—— {@code after_sale} 表。
 *
 * <p>状态机（{@link org.icedamericanomall.trade.domain.enums.AfterSaleStatusEnum}）：
 * 1-PENDING 待审核 → 2-APPROVED 同意 / 3-REJECTED 拒绝；退货退款 2 → 4-RETURNED（确认收到退货）；
 * 资金终态 5-REFUNDED（仅退款走 2→5，退货退款走 4→5，两条边共用）。
 * REFUNDED 是资金终态，禁止再被审核改回其它状态（否则等于「撤销退款」）。
 */
@Data
public class AfterSale {

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
    private LocalDateTime updateTime;
}
