package org.icedamericanomall.marketing.infrastructure.persistence.groupbuy;

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
 * 拼团活动表映射对象（PO）—— {@code group_buy_activity} 表（V5.14，ADR-049），
 * 仅存在于基础设施持久化层。
 *
 * <p>金额字段为整数分（groupPrice）。{@code version} 为乐观锁列：管理端编辑时随聚合携带比对。
 * {@code update_time} 由 DB {@code ON UPDATE CURRENT_TIMESTAMP} 维护，PO 不映射。
 */
@Data
@TableName("group_buy_activity")
public class GroupBuyActivityPO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    /** 商家 user_id（店铺级活动） */
    private Long sellerId;
    private Long productId;
    /** SKU ID（拼团仅该 SKU 参与） */
    private Long skuId;
    /** 拼团价（分，>0） */
    private Integer groupPrice;
    /** 成团人数（≥2） */
    private Integer requiredMembers;
    /** 成团时限（小时） */
    private Integer timeLimitHours;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 1=未开始 2=进行中 3=已结束（时间窗推导，写入时刻快照） */
    private Integer status;
    @Version
    private Integer version;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
