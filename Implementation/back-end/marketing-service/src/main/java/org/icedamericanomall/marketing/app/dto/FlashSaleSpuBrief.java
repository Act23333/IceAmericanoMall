package org.icedamericanomall.marketing.app.dto;

/**
 * 秒杀商品展示摘要（应用层读模型，不可变）。
 *
 * <p>秒杀表（{@code flash_sale}）只存 {@code spu_id}/{@code sku_id}，而契约 §4.10.2 的
 * {@code FlashSaleVO} 需投影商品名/主图与划线原价；本摘要即这组跨聚合读数的载体，
 * 由 {@code FlashSaleSpuBriefQuery} 端口按 <b>SKU ID 批量</b>装配（无 N+1）。
 *
 * <p>刻意不用 {@code DTO}/{@code CO} 后缀：它不跨进程、不出现在任何线格式契约中。
 *
 * @param skuId         关联键（与 {@code flash_sale.sku_id} 对齐）
 * @param productName   商品名称（{@code spu.name}）
 * @param productImage  商品主图（{@code spu.main_image}）
 * @param originalPrice 划线原价（分；{@code sku.original_price}，未设划线价时为 {@code null}）
 */
public record FlashSaleSpuBrief(
        Long skuId,
        String productName,
        String productImage,
        Integer originalPrice) {
}
