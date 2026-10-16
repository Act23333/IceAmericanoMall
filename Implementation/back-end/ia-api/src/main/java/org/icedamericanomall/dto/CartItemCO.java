package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Cart item DTO for inter-service Feign calls (cart-service → trade-service).
 *
 * <p><b>标识口径（ADR-061 §决策 2）</b>：{@code skuId} 为 <b>SKU 业务键</b> 32-hex
 * （{@code sku.sku_id}），不是技术主键 —— 与 {@code CartItemVO.sku_id} 同型同义，
 * 也与订单项快照口径一致。{@code cartItemId} 仍是 {@code cart.id} <b>技术主键</b>：
 * 它是 cart 域的<b>行主键</b>（下单后删除已购行的定位键），不属「SPU/SKU 实体标识」，不出线格式。
 */
@Data
public class CartItemCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Cart item primary key (for removal after order). */
    private Long cartItemId;

    /** SKU 业务键（{@code sku.sku_id} 的 32-hex 形态）。 */
    private String skuId;

    /** SPU 业务键（{@code spu.spu_id} 的 32-hex 形态；由 cart 编排层按 I13 回填）。 */
    private String spuId;

    private String productName;

    private String spec;

    private String image;

    /** Price in cents. */
    private Integer price;

    private Integer quantity;
}
