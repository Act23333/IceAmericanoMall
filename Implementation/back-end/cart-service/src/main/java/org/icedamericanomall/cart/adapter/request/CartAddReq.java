package org.icedamericanomall.cart.adapter.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 加入购物车请求（C2 {@code POST /api/cart/item}）。
 *
 * <p><b>{@code sku_id} 口径（契约 §1.8② + ADR-061 §决策 2）</b>：<b>SKU 业务键</b>
 * 32-hex {@code String}，与 {@code CartItemVO.sku_id}、C4/C5 路径变量<b>同型同义</b>；
 * 服务端负责解析为内部技术键（{@code cart.sku_id} 保持技术键不动——A 案）。
 * 格式非法或 SKU 不存在 → {@code SKU_NOT_FOUND(3016)}（fail-closed，不静默插入脏行）。
 */
@Data
public class CartAddReq {
    @NotBlank(message = "SKU ID（业务键）不能为空")
    private String skuId;
    @Min(value = 1, message = "数量至少为1")
    private Integer quantity = 1;
}
