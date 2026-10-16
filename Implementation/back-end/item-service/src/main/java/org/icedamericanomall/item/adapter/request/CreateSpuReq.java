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

    @NotBlank(message = "商品名称不能为空")
    private String name;

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
