package org.icedamericanomall.item.adapter.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 库存扣减 / 恢复入参（I14 {@code POST /internal/item/sku/deduct}、I15 {@code /restore}）。
 *
 * <p><b>标识口径（ADR-058 §1 + ADR-061 §决策 2）</b>：{@code skuId} 为 <b>SKU 业务键</b>
 * 32-hex {@code String}（与 {@code StockOpCO.skuId} 同型同义）。解析在 service 层
 * （{@code SkuManager.deductStock/restoreStock}）按 {@code sku.sku_id} 唯一索引完成。
 */
@Data
public class StockOpReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "SKU ID（业务键）不能为空")
    private String skuId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于0")
    private Integer quantity;
}
