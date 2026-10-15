package org.icedamericanomall.item.app.dto;

/**
 * SKU 所属商品的展示事实（应用层读模型，不可变）。
 *
 * <p><b>存在理由</b>：{@code sku} 表没有商品名列 —— 契约
 * （{@code contract/05-API-Specification.md} §4.14.5 I13）声明的 {@code SkuCO}
 * 要求投影 {@code product_name} / {@code seller_id} / {@code category_id} /
 * {@code freight_template_id}，这四列全部属于 {@code product} 表。此前
 * {@code SkuConverter.toCO} 无源可映射，四字段<b>恒为 null</b>：购物车
 * {@code product_name} 恒空、下单快照 {@code product_name}/{@code seller_id}
 * 一并丢失（详见 ADR-057 与本次交付报告）。
 *
 * <p><b>为何不塞进 {@code Sku} 聚合</b>：这四列不属于 SKU 聚合（ADR-025 小聚合 +
 * 聚合间按 ID 引用），本记录即跨聚合读数的载体，由 {@code SkuProductDisplayQuery}
 * 端口按 <b>SKU ID 批量</b>装配（整页一次查询，无 N+1）。
 *
 * <p>刻意不用 {@code DTO}/{@code CO} 后缀：它不跨进程、不出现在任何线格式契约中。
 *
 * @param skuId             关联键（与 {@code sku.id} 对齐）
 * @param productName       商品名称（{@code product.name}）
 * @param sellerId          所属商家 ID（{@code product.seller_id}，= 商家 user_id 口径）
 * @param categoryId        所属类目 ID（{@code product.category_id}）
 * @param freightTemplateId 绑定的运费模板 ID（{@code product.freight_template_id}，可为 {@code null}）
 */
public record SkuProductDisplay(
        Long skuId,
        String productName,
        Long sellerId,
        Long categoryId,
        Long freightTemplateId) {
}
