package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 库存操作 DTO（trade-service → item-service，I14/I15 扣减与恢复）。
 *
 * <p><b>标识口径（ADR-058 §1 + ADR-061 §决策 2）</b>：{@code skuId} 为 <b>SKU 业务键</b>
 * 32-hex {@code String} —— 与 {@code SkuCO.skuId}/{@code OrderItem.skuId} 同型同义，
 * 使「订单项 → 扣/还库存」无需任何键空间转换。item-service 侧按
 * {@code sku.sku_id} 唯一索引解析为内部技术键（A 案：{@code sku.id} 保持技术键）。
 */
@Data
public class StockOpCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SKU 业务键（32-hex）。 */
    private String skuId;

    private Integer quantity;
}
