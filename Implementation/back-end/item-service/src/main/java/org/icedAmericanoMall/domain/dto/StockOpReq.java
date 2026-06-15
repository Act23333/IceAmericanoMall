package org.icedAmericanoMall.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Request DTO for stock deduction / restoration operations.
 */
@Data
public class StockOpReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "SKU ID不能为空")
    private Long skuId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于0")
    private Integer quantity;
}
