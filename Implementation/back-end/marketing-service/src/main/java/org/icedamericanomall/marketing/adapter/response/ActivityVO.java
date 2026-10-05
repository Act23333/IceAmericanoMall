package org.icedamericanomall.marketing.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 营销活动视图对象（适配层出参）—— 活动中心 M12/M13 口径（05-API-Specification §4.10.3，V5.11 定稿）。
 *
 * <p>数据源 = {@code promotion} 表进行中活动：{@code type} 为对外契约字符串
 * （{@code FULL_REDUCE} / {@code LIMIT_DISCOUNT}，非数字编码）；金额一律整数分——
 * 满减 {@code discountValue} = 减免金额；满折 {@code discountValue} = 折扣率万分比（8500=8.5折），
 * {@code maxDiscount} = 封顶减免（0=不封顶）。
 *
 * <p>与下单计价同源（ADR-043 同 {@code promotion} 表），用户所见即所得；不透出技术字段
 * （{@code status} 快照 / {@code version}）。
 */
@Data
public class ActivityVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    /** 活动类型契约字符串：FULL_REDUCE 满减 / LIMIT_DISCOUNT 满折 */
    private String type;
    private String name;
    private Long sellerId;
    /** 门槛（分） */
    private Integer threshold;
    /** 满减=减免金额（分）；满折=折扣率万分比（8500=8.5折） */
    private Integer discountValue;
    /** 满折封顶减免额（分）；0=不封顶 */
    private Integer maxDiscount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
