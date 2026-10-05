package org.icedamericanomall.item.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String productId;
    private Long sellerId;
    private Long categoryId;
    private String name;
    private String mainImage;
    private String description;
    private String brand;
    private Integer soldCount;
    private Integer commentCount;
    /** 列表页展示价：该商品在售 SKU 的最低价（分）；无在售 SKU 时为 null。 */
    private Integer minPrice;
    private Integer status;
    private LocalDateTime publishTime;
    /** 绑定的运费模板 ID（F2 运费模型，V5.6）；null = 未绑定 → 下单回落商家默认模板。 */
    private Long freightTemplateId;
    /** 仅详情页返回完整 SKU 列表；列表页不携带。 */
    private List<SkuVO> skus;
    /**
     * 商品详情页促销标签（F10，契约 §4.4.x P2 V5.11 增量）：所属店铺进行中满减/满折活动；
     * fail-open —— marketing 不可用/无活动 → 空列表，不阻断详情页。列表页不携带。
     */
    private List<PromotionTagVO> promotions;
}
