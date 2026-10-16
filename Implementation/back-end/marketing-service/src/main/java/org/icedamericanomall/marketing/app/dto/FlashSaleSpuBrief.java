package org.icedamericanomall.marketing.app.dto;

/**
 * 秒杀商品展示摘要（应用层读模型，不可变）。
 *
 * <p>秒杀表（{@code flash_sale}）只存 {@code spu_id}/{@code sku_id} 两个<b>技术键</b>
 * （A 案：表结构不动，ADR-061 §决策 4），而契约 §4.10.2 的 {@code FlashSaleVO} 需投影
 * {@code spu_id}/{@code sku_id}（<b>业务键</b> 32-hex）、{@code spu_name}/{@code spu_image}
 * 与划线原价；本摘要即这组跨聚合读数的载体，由 {@code FlashSaleSpuBriefQuery} 端口按
 * <b>SKU 技术键批量</b>装配（无 N+1），并<b>顺带</b>带出两个业务键（ADR-058 §3：读模型
 * 按技术键取数时必须顺带返回业务标识）。
 *
 * <p>刻意不用 {@code DTO}/{@code CO} 后缀：它不跨进程、不出现在任何线格式契约中。
 *
 * @param skuId         关联键（与 {@code flash_sale.sku_id} 对齐；技术键）
 * @param spuId         SPU 技术键（{@code spu.id}；写路径解析用，不出线格式）
 * @param skuBusinessId SKU 业务键（{@code sku.sku_id} 的 32-hex 形态；出线格式）
 * @param spuBusinessId SPU 业务键（{@code spu.spu_id} 的 32-hex 形态；出线格式）
 * @param spuName       SPU 名（{@code spu.name}；原 {@code productName}，ADR-061 §决策 5）
 * @param mainImage     SPU 主图（{@code spu.main_image}；线上名统一 {@code main_image}）
 * @param originalPrice 划线原价（分；{@code sku.original_price}，未设划线价时为 {@code null}）
 */
public record FlashSaleSpuBrief(
        Long skuId,
        Long spuId,
        String skuBusinessId,
        String spuBusinessId,
        String spuName,
        String mainImage,
        Integer originalPrice) {
}
