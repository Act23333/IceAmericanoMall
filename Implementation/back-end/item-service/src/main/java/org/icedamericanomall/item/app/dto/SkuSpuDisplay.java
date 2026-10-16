package org.icedamericanomall.item.app.dto;

/**
 * SKU 所属商品的展示事实（应用层读模型，不可变）。
 *
 * <p><b>存在理由</b>：{@code sku} 表没有商品名列 —— 契约
 * （{@code contract/05-API-Specification.md} §4.14.5 I13）声明的 {@code SkuCO}
 * 要求投影 {@code spu_name} / {@code seller_id} / {@code category_id} /
 * {@code freight_template_id}，这四列全部属于 {@code spu} 表。此前
 * {@code SkuConverter.toCO} 无源可映射，四字段<b>恒为 null</b>：购物车名称恒空、
 * 下单快照 {@code spu_name}/{@code seller_id} 一并丢失（详见 ADR-057 与本次交付报告）。
 *
 * <p><b>业务键维度（ADR-058 §3 / ADR-061）</b>：线格式（{@code SkuCO}/{@code SkuVO}/
 * 订单项快照）的 SPU/SKU 标识一律为业务键 32-hex —— 本记录随同一条
 * {@code sku LEFT JOIN spu} <b>顺带</b>带出两个业务键，调用方<b>不需要</b>为拿业务键
 * 再打一次库或另开端点。{@code skuId}/{@code spuId} 保持<b>技术键</b>语义，只在应用层内部
 * 作 join / 映射用，不得进入任何 VO/DTO。
 *
 * <p><b>为何不塞进 {@code Sku} 聚合</b>：这些列不属于 SKU 聚合（ADR-025 小聚合 +
 * 聚合间按 ID 引用），本记录即跨聚合读数的载体，由 {@code SkuSpuDisplayQuery}
 * 端口按 <b>SKU ID / SKU 业务键批量</b>装配（整页一次查询，无 N+1）。
 *
 * <p>刻意不用 {@code DTO}/{@code CO} 后缀：它不跨进程、不出现在任何线格式契约中。
 *
 * @param skuId             SKU 技术键（与 {@code sku.id} 对齐；内部映射键）
 * @param skuBusinessId     SKU 业务键（{@code sku.sku_id} BINARY(16) 的 32-hex 形态）
 * @param spuId             SPU 技术键（{@code sku.spu_id}；内部关联键）
 * @param spuBusinessId     SPU 业务键（{@code spu.spu_id} BINARY(16) 的 32-hex 形态）
 * @param spuName           SPU 名称（{@code spu.name}）
 * @param sellerId          所属商家 ID（{@code spu.seller_id}，= 商家 user_id 口径）
 * @param categoryId        所属类目 ID（{@code spu.category_id}）
 * @param freightTemplateId 绑定的运费模板 ID（{@code spu.freight_template_id}，可为 {@code null}）
 */
public record SkuSpuDisplay(
        Long skuId,
        String skuBusinessId,
        Long spuId,
        String spuBusinessId,
        String spuName,
        Long sellerId,
        Long categoryId,
        Long freightTemplateId) {
}
