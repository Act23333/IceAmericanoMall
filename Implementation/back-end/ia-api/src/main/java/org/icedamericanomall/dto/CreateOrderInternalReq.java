package org.icedamericanomall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 内部订单创建请求（marketing-service → trade-service Feign 调用，I14）。
 *
 * <p>营销服务通过此 DTO 委托交易服务创建秒杀订单。
 *
 * <p><b>标识口径（ADR-058 §1 + ADR-061 §决策 2）</b>：{@code skuId} 为 <b>SKU 业务键</b>
 * 32-hex {@code String} —— 与 {@code SkuCO.skuId}/{@code CartItemCO.skuId}/
 * {@code order_item.sku_id} 同型同义，trade 侧直接按业务键取商品快照与写订单项标识列。
 * 另：{@code productName}/{@code skuSpec}/{@code image} 三个快照字段已无调用方（快照一律由
 * trade 侧从 I13 取，避免调用方造快照造成口径分叉），本批一并移除。
 */
@Data
public class CreateOrderInternalReq implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull private Long userId;
    /** SKU 业务键（32-hex）。 */
    @NotBlank private String skuId;
    @NotNull private Long sellerId;
    @NotNull private Integer quantity;
    @NotNull private Integer flashPrice;   // 秒杀价(分)
    @NotNull private Long flashId;         // 秒杀活动ID
    @NotNull private Long addressId;
}
