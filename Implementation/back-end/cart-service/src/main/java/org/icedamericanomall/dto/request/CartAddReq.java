package org.icedamericanomall.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CartAddReq {
    @NotNull(message = "SKU ID不能为空")
    private Long skuId;
    @Min(value = 1, message = "数量至少为1")
    private Integer quantity = 1;
}