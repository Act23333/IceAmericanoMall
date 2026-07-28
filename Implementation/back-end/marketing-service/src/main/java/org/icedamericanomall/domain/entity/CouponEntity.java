package org.icedamericanomall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("coupon")
public class CouponEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    private String couponId;
    private String name;
    /** @deprecated V4.0: 请使用 discountType */
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

    // V4.0: 多维度优惠券模型（京东标准）
    /** 优惠计算类型: 1=FIXED, 2=PERCENTAGE, 3=CASH_COUPON */
    private Integer discountType;
    /** 券类别: 1=PLATFORM, 2=SHOP, 3=FLASH_SALE, 4=EXCLUSIVE */
    private Integer couponCategory;
    /** 获取方式: 1=FREE_CLAIM, 2=PAID_PURCHASE, 3=INVITATION, 4=AUTO_ISSUE */
    private Integer grantType;
    /** 总量模型: 1=LIMITED, 2=UNLIMITED */
    private Integer stockType;
    /** 领取方式: 1=NORMAL, 2=NEED_GRAB, 3=PLATFORM_EXCLUSIVE */
    private Integer grabType;
    /** 付费券价格(分), grantType=PAID_PURCHASE时必填 */
    private Integer priceInCents;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
