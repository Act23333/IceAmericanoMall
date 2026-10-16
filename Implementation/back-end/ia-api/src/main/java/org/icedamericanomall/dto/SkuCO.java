package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class SkuCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;          // 技术主键 (sku.id)
    private Long spuId;
    private Long sellerId;
    private String productName;
    private String spec;
    private Integer price;
    private Integer stock;
    private String image;
    private Integer status;

    // V4.0: 库存类型 + 热销标识 + 销售标签（京东/淘宝标准）
    private Integer stockType;   // 1=LIMITED, 2=UNLIMITED, 3=PRESALE
    private Boolean isHot;
    private String hotReason;
    private String salesTags;

    // V4.3: 品类ID（从产品表关联，用于优惠券品类范围校验）
    private Long categoryId;

    /**
     * V5.6（F2 运费模型）：商品绑定的运费模板 ID（从 spu 表关联，可为 null）。
     * <p>null = 未绑定 → 下单计价回落商家默认模板（最早创建的启用模板）；模板被删除时
     * 经 {@code ON DELETE SET NULL} 自动回落（见 04-Data-Model §十二 12.2）。
     */
    private Long freightTemplateId;
}
