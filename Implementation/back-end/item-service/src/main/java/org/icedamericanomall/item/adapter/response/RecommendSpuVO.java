package org.icedamericanomall.item.adapter.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 相似商品推荐卡（P27，ADR-046）—— 商品详情页「相似商品」推荐位展示对象。
 *
 * <p>字段集对齐商品列表卡（{@link SpuVO} 列表口径）：展示真相源是 MySQL，
 * 组装自本地库最新在售数据，不透出 ES 文档（可能滞后）。金额单位为分。
 */
@Data
public class RecommendSpuVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商品 id（技术主键，外部契约寻址口径）。 */
    private Long spuId;

    /** 商品名称。 */
    private String name;

    /** 主图。 */
    private String mainImage;

    /** 展示价（分）：在售 SKU 最低价，与商品列表卡 {@code minPrice} 口径一致；无在售 SKU 时为 null。 */
    private Integer price;

    /** 销量。 */
    private Integer soldCount;
}
