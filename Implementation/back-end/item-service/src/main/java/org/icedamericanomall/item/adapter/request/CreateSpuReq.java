package org.icedamericanomall.item.adapter.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class CreateSpuReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "类目ID不能为空")
    private Long categoryId;

    /**
     * SPU 名 —— 请求体键 <b>{@code spu_name}</b>（P3 商品发布；契约 §1.8④）。
     *
     * <p><b>O2 裁决（2026-10-18）：写侧一并收口</b>（原 {@code name}）。理由：同一实体只许一个名字 ——
     * 留着写侧 {@code name} 会形成「读出侧 {@code spu_name} / 写侧 {@code name}」的第三种口径，
     * 下一个照 §1.8④ 强读法的人必然改错一侧。实测前端零调用 P3/P4，BREAKING 面仅商家端。
     */
    @NotBlank(message = "商品名称不能为空")
    private String spuName;

    private String mainImage;
    private String description;
    private String brand;

    /**
     * 绑定的运费模板 ID（F2 运费模型，V5.6，可空）。
     * <p>null = 不绑定 → 下单按商家默认模板（最早创建的启用模板）计费；
     * 非空时必须归属当前商家，否则 {@code FREIGHT_TEMPLATE_NOT_FOUND(3041)}。
     */
    private Long freightTemplateId;

    @NotEmpty(message = "至少需要一个SKU")
    private List<SkuReq> skus;

    @Data
    public static class SkuReq implements Serializable {
        private static final long serialVersionUID = 1L;

        @NotBlank(message = "规格描述不能为空")
        private String spec;

        @NotNull(message = "价格不能为空")
        private Integer price;

        @NotNull(message = "库存不能为空")
        private Integer stock;

        private String image;

        // V4.0: 库存类型 + 热销标识
        private Integer stockType;
        private Boolean isHot;
        private String hotReason;
        private String salesTags;
    }

    // V4.0: 商品级销售标签
    private String salesTags;
}
