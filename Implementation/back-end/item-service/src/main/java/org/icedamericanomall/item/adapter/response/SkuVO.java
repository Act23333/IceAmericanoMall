package org.icedamericanomall.item.adapter.response;

import lombok.Data;

import java.io.Serializable;

/**
 * SKU 视图对象（商品详情 / SKU 列表）。
 *
 * <p><b>标识口径（ADR-058 §1 / ADR-061 §决策 2）</b>：{@code spuId} / {@code skuId} 均为
 * <b>业务键</b> 32-hex {@code String}（{@code spu.spu_id} / {@code sku.sku_id}）。
 * {@code id} 是 {@code sku.id} <b>资源行主键</b>（渲染/定位用），按 §1.8 例外条款保持现状。
 *
 * <p><b>名称字段（ADR-061 §决策 5）</b>：{@code spuName} ← {@code spu.name}
 * （原 {@code productName}，词汇与 SPU 实体对齐）。
 */
@Data
public class SkuVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 资源行主键（{@code sku.id}）—— 渲染用，不属「SPU/SKU 实体标识」（§1.8 例外）。 */
    private Long id;

    /** SKU 业务键（{@code sku.sku_id} 的 32-hex 形态）。 */
    private String skuId;

    /** SPU 业务键（{@code spu.spu_id} 的 32-hex 形态）。 */
    private String spuId;

    /** SPU 名（{@code spu.name}；原 {@code productName}）。 */
    private String spuName;

    private String spec;
    private Integer price;
    /** 原价（分），划线价 */
    private Integer originalPrice;
    private Integer stock;
    private String image;
    private Integer soldCount;
    private Integer status;

    // V4.0: 库存类型 + 热销标识 + 销售标签
    private Integer stockType;
    private Boolean isHot;
    private String hotReason;
    private String salesTags;
}
