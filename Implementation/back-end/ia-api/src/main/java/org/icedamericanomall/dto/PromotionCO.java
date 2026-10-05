package org.icedamericanomall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 生效促销活动（trade-service 定价中心消费，契约 §4.14.8b I44，V5.8）。
 *
 * <p>只返回「进行中」活动（时间窗推导，ADR-043）；金额一律整数分。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PromotionCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 活动 id（技术主键，flash_sale 同口径无业务唯一键） */
    private Long id;

    /** 活动名称 */
    private String name;

    /** 商家 user_id */
    private Long sellerId;

    /** 1=满减(FIXED) 2=满折(PERCENTAGE) */
    private Integer type;

    /** 门槛：订单商品总额 ≥ threshold 生效（分） */
    private Integer threshold;

    /** 满减=减免金额（分）；满折=折扣率万分比（8500=8.5折） */
    private Integer discountValue;

    /** 满折封顶减免额（分）；0=不封顶 */
    private Integer maxDiscount;
}
