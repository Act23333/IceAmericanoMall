package org.icedamericanomall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("user_coupon")
public class UserCouponEntity implements Serializable {
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
