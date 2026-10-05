package org.icedamericanomall.marketing.infrastructure.persistence.promotion;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 促销活动表映射对象（PO）—— {@code promotion} 表（V5.8 迁移），仅存在于基础设施持久化层。
 *
 * <p>金额字段均为整数分（threshold/discountValue/maxDiscount）；满折折扣率为万分比
 * （8500=8.5折）。{@code version} 为乐观锁列：管理端编辑时随聚合携带比对。
 * {@code update_time} 由 DB {@code ON UPDATE CURRENT_TIMESTAMP} 维护，PO 不映射。
 */
@Data
@TableName("promotion")
public class PromotionPO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long sellerId;
    /** 1=满减(FIXED) 2=满折(PERCENTAGE) */
    private Integer type;
    /** 门槛：订单商品总额 ≥ threshold 生效（分） */
    private Integer threshold;
    /** 满减=减免金额（分）；满折=折扣率万分比（8500=8.5折） */
    private Integer discountValue;
    /** 满折封顶减免额（分）；0=不封顶（满减恒等于 discount_value） */
    private Integer maxDiscount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 1=未开始 2=进行中 3=已结束（时间窗推导，写入时刻快照） */
    private Integer status;
    @Version
    private Integer version;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
