package org.icedamericanomall.marketing.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户优惠券视图对象（适配层出参）—— 报文形状与迁移前保持一致。
 */
@Data
public class UserCouponVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long couponId;
    private Integer status;
    private String usedOrderNo;
    private LocalDateTime useTime;
    private LocalDateTime createTime;
}
