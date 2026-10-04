package org.icedamericanomall.cart.adapter.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CartUpdateReq {
    @NotNull(message = "SKU ID不能为空")
    private Long skuId;

    /**
     * 数量：不做下限校验 —— 数量 ≤ 0 表示删除该购物车项（京东标准）。
     */
    @NotNull(message = "数量不能为空")
    private Integer quantity;
}
