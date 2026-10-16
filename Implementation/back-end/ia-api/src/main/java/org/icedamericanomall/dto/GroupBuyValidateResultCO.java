package org.icedamericanomall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 拼团下单前置校验结果（trade 定价/建单依据，契约 §4.14.8c I52，V5.14）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupBuyValidateResultCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 拼团活动 id */
    private Long activityId;

    /** 团号（参团时回显；开团时为 null，开团动作发生在 I53 join） */
    private String teamNo;

    /** 拼团价（分，快照进订单 subTotal） */
    private Integer groupPrice;

    /** 成团人数 */
    private Integer requiredMembers;

    /** 成团时限（小时） */
    private Integer timeLimitHours;

    /** 活动绑定 SKU ID（服务端权威绑定：trade 据此查 item-service 做订单快照与扣库存，不信任客户端传入，V5.14.1） */
    /** SKU 业务键（32-hex）—— I52 回执与 I13 入参同口径（ADR-061 §决策 2）。 */
    private String skuId;
}
