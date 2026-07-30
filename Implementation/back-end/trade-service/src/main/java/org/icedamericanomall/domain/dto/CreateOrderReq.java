package org.icedamericanomall.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class CreateOrderReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long addressId;

    @NotEmpty(message = "购物车项不能为空")
    private List<Long> cartItemIds;

    /** @deprecated V4.3: 单券模式，保留兼容。新代码请使用 userCouponIds */
    private Long userCouponId;
    /** V4.3: 多券叠加模式（user_coupon.id 列表） */
    private List<Long> userCouponIds;

    private String remark;
}
