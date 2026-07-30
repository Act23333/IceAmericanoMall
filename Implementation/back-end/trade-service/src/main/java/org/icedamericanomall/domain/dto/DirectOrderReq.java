package org.icedamericanomall.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * V4.0: 立即购买请求 — 京东标准（商品详情页直接下单，不操作购物车）。
 */
@Data
public class DirectOrderReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "SKU ID不能为空")
    private Long skuId;

    @Min(value = 1, message = "数量至少为1")
    private Integer quantity = 1;

    @NotNull(message = "地址ID不能为空")
    private Long addressId;

    /** @deprecated V4.3: 单券模式，保留兼容 */
    private Long userCouponId;
    /** V4.3: 多券叠加模式 */
    private List<Long> userCouponIds;
}
