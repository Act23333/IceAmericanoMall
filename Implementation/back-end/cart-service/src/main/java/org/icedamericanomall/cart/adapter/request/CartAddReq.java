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
 *
 * <p><b>两种失败语义分开（批次 ③ 拆双义错误码，2026-10-18）</b>：
 * <ul>
 *   <li><b>形态非法</b>（拼错 / 长度错 / 非 hex）→ {@code PARAM_ERROR(1101)} +
 *       <b>HTTP 400</b>，在<b>适配层</b> {@code IdGenerator#requireWire} fail-fast
 *       （ADR-062 §决策 7：编解码唯一出口 + 形态非法 400）；</li>
 *   <li><b>SKU 不存在</b>（形态合法的业务键解析不到）→ {@code SKU_NOT_FOUND(3016)}，
 *       fail-closed，不静默插入脏行。</li>
 * </ul>
 * 本字段<b>不</b>用 {@code @Pattern} 表达形态约束：形态口径只允许有一处实现
 * （{@code IdGenerator#requireWire}），正则抄到这里会形成第二套真相。
 */
@Data
public class CartAddReq {
    @NotBlank(message = "SKU ID（业务键）不能为空")
    private String skuId;
    @Min(value = 1, message = "数量至少为1")
    private Integer quantity = 1;
}
