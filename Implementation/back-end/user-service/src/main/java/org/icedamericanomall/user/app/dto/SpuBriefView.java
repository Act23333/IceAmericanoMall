package org.icedamericanomall.user.app.dto;

/**
 * 商品展示摘要（应用层读模型，不可变）。
 *
 * <p>收藏/足迹这类「只存商品 ID」的本地表，其列表页需要商品域的名称、主图、价格与状态；
 * 该摘要即这一组跨聚合（跨服务）读数的载体，由 {@code FavoriteSpuBriefQuery} 端口按
 * <b>ID 批量</b>装配，避免逐行查询（N+1）。
 *
 * <p>刻意不叫 {@code XxxDTO}/{@code XxxCO}：它不跨进程、不出现在任何线格式契约中，
 * 只在应用层与适配层之间传递。
 *
 * @param spuId   商品 ID（与收藏行的 {@code spu_id} 对齐的关联键）
 * @param productName 商品名称
 * @param mainImage   商品主图 URL
 * @param price       在售 SKU 最低价（分）；无在售 SKU 时为 {@code null}
 * @param status      商品状态：1=上架，2=下架，3=删除（{@code SpuStatusEnum}）
 */
public record SpuBriefView(
        Long spuId,
        String productName,
        String mainImage,
        Integer price,
        Integer status) {
}
