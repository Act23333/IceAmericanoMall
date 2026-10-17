package org.icedamericanomall.item.adapter.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 相似商品推荐卡（P27，ADR-046）—— 商品详情页「相似商品」推荐位展示对象。
 *
 * <p>字段集对齐商品列表卡（{@link SpuVO} 列表口径）：展示真相源是 MySQL，
 * 组装自本地库最新在售数据，不透出 ES 文档（可能滞后）。金额单位为分。
 *
 * <p><b>标识口径（ADR-062 §决策 3，闭合 §后果 E3）</b>：{@code spuId} 为 SPU
 * <b>业务键</b> 32-hex（{@code spu.spu_id}），与详情路径变量 {@code {spuId}}
 * （{@code GET /api/item/spu/{spuId}}）<b>同型同义</b> —— 前端「列表拿到 → 直接回链详情」
 * 在类型层面成立；此前保留技术键的「有意口径并存」随本批收口（其并存理由
 * 「路径变量保持技术键」已随 ADR-062 §决策 2 消失）。
 */
@Data
public class RecommendSpuVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** SPU 业务键（32-hex，{@code spu.spu_id}；前端回链 {@code GET /api/item/spu/{spuId}} 直接用）。 */
    private String spuId;

    /** SPU 名 —— 线上键 {@code spu_name}（契约 §1.8④；P27 相似卡 / P35 搭配卡共用本卡）。 */
    private String spuName;

    /** 主图。 */
    private String mainImage;

    /** 展示价（分）：在售 SKU 最低价，与商品列表卡 {@code minPrice} 口径一致；无在售 SKU 时为 null。 */
    private Integer price;

    /** 销量。 */
    private Integer soldCount;
}
