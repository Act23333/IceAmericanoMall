package org.icedamericanomall.item.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品详情促销标签（F10，契约 §4.4.x P2 V5.11 增量 {@code promotions}）——
 * 商品所属店铺「进行中」满减/满折活动的展示对象。
 *
 * <p>数据源与下单计价同源（I44 生效促销查询，promotion 表）——用户所见即所得；
 * 展示场景 fail-open：marketing 不可用/无活动 → {@code promotions} 为空列表，不阻断详情页。
 * 金额单位为分。
 */
@Data
public class PromotionTagVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 标签类型：FULL_REDUCE=满减 / LIMIT_DISCOUNT=满折（契约字符串，PromotionCO.type 1/2 映射）。 */
    private String type;

    /** 活动名称。 */
    private String name;

    /** 门槛（分）：订单商品总额 ≥ threshold 生效。 */
    private Integer threshold;

    /** 满减=减免金额（分）；满折=折扣率万分比（8500=8.5折）。 */
    private Integer discountValue;

    /** 满折封顶减免额（分）；0=不封顶。 */
    private Integer maxDiscount;

    /** 活动截止时间；I44 契约（PromotionCO）暂未透出该字段，当前恒为 null，预留契约扩展。 */
    private LocalDateTime endTime;
}
