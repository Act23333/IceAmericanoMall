package org.icedamericanomall.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class CreateProductReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "类目ID不能为空")
    private Long categoryId;

    @NotBlank(message = "商品名称不能为空")
    private String name;

    private String mainImage;
    private String description;
    private String brand;

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
    }
}
