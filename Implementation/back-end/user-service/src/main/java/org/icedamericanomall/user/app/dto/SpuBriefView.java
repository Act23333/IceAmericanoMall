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
 * <p><b>业务键维度（ADR-058 §3 / ADR-061）</b>：本记录随同一条联表查询带出
 * {@code spu.spu_id}（32-hex 业务键）—— 线格式（{@code FavoriteVO.spu_id} / 足迹
 * {@code spu_id}）只出业务键，而 {@code favorite.spu_id} / {@code spu_view_log.spu_id}
 * 等<b>同库内部外键保持技术键不动</b>（A 案），故解析与回填都经本记录完成。
 *
 * @param spuId          商品技术键（与收藏行 {@code spu_id} 对齐的关联键；内部用）
 * @param spuBusinessId  商品业务键（{@code spu.spu_id} 的 32-hex 形态；出线格式）
 * @param productName    商品名称
 * @param mainImage      商品主图 URL
 * @param price          在售 SKU 最低价（分）；无在售 SKU 时为 {@code null}
 * @param status         商品状态：1=上架，2=下架，3=删除（{@code SpuStatusEnum}）
 */
public record SpuBriefView(
        Long spuId,
        String spuBusinessId,
        String productName,
        String mainImage,
        Integer price,
        Integer status) {
}
