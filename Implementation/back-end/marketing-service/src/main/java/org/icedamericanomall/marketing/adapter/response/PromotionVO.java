package org.icedamericanomall.marketing.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 促销活动视图对象（适配层出参）—— 管理端 AD48 创建回执口径（05-API-Specification §4.13.8b）。
 *
 * <p>{@code status} 为时间窗推导状态在写入时刻的快照（1未开始/2进行中/3已结束）；
 * 不透出技术字段（{@code version} 等）。
 */
@Data
public class PromotionVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private Long sellerId;
    private Integer type;
    /** 门槛（分） */
    private Integer threshold;
    /** 满减=减免金额（分）；满折=折扣率万分比（8500=8.5折） */
    private Integer discountValue;
    /** 满折封顶减免额（分）；0=不封顶 */
    private Integer maxDiscount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
}
