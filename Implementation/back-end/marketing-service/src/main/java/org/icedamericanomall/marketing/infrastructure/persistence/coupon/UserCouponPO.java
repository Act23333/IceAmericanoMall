package org.icedamericanomall.marketing.infrastructure.persistence.coupon;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户优惠券表映射对象（PO）—— {@code user_coupon} 表，仅存在于基础设施持久化层。
 */
@Data
@TableName("user_coupon")
public class UserCouponPO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long couponId;
    /** 1=未使用, 2=已使用, 3=已过期 */
    private Integer status;
    private String usedOrderNo;
    private LocalDateTime useTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
