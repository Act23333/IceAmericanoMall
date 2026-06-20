package org.icedAmericanoMall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("coupon")
public class CouponEntity implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    private String couponId;
    private String name;
    /** 1=满减, 2=折扣 */
    private Integer type;
    /** 满减=分, 折扣=百分比(85=8.5折) */
    private Integer value;
    /** 最低消费金额（分） */
    private Integer minAmount;
    private Integer totalQty;
    private Integer issuedQty;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    /** 商家ID（NULL=平台券） */
    private Long sellerId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
