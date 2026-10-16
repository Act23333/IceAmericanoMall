package org.icedamericanomall.cart.adapter.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改购物车项数量请求（C3 {@code PUT /api/cart/item}）。
 *
 * <p><b>{@code sku_id} 口径（契约 §1.8② + ADR-061 §决策 2）</b>：<b>SKU 业务键</b> 32-hex。
 * 解析不到 → 幂等空操作（目标行本就不存在，与「删除不存在的购物车项」同语义）。
 */
@Data
public class CartUpdateReq {
    @NotBlank(message = "SKU ID（业务键）不能为空")
    private String skuId;

    /**
     * 数量：不做下限校验 —— 数量 ≤ 0 表示删除该购物车项（京东标准）。
     */
    @NotNull(message = "数量不能为空")
    private Integer quantity;
}
