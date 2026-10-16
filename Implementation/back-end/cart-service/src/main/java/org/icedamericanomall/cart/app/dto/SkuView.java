package org.icedamericanomall.cart.app.dto;

/**
 * 购物车项所需的 SKU 事实（应用层读模型，不可变）。
 *
 * <p><b>存在理由</b>：{@code cart} 表只存 {@code user_id + sku_id + quantity + selected}，
 * 没有商品名/规格/单价/图片；且 {@code cart.sku_id} 是 {@code sku.id} 的<b>技术外键</b>，
 * 而线格式（{@code CartItemVO.sku_id}）出的是 <b>SKU 业务键</b>（ADR-061 §决策 2）。
 * 本记录把「cart 行技术键 → 业务键 + 展示列」一次带出，避免为拿业务键再打一次库。
 *
 * <p>刻意不用 {@code DTO}/{@code CO} 后缀：它不跨进程、不出现在任何线格式契约中
 * （线格式字段名由 {@code CartItemVO} 定义）。
 *
 * @param skuTechnicalId SKU 技术键（{@code sku.id}；与 {@code cart.sku_id} 对齐）
 * @param skuBusinessId  SKU 业务键（{@code sku.sku_id} 的 32-hex 形态；出线格式）
 * @param spuBusinessId  SPU 业务键（{@code spu.spu_id} 的 32-hex 形态；出线格式）
 * @param spuName        SPU 名（{@code spu.name}；原 {@code productName}，ADR-061 §决策 5）
 * @param spec          规格描述（{@code sku.spec}）
 * @param price         售价（分；{@code sku.price}）
 * @param image         图片（{@code sku.image}）
 */
public record SkuView(
        Long skuTechnicalId,
        String skuBusinessId,
        String spuBusinessId,
        String spuName,
        String spec,
        Integer price,
        String image) {
}
