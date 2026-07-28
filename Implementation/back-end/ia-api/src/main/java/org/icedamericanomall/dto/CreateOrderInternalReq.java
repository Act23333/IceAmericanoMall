package org.icedamericanomall.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * V4.1: 内部订单创建请求（marketing-service → trade-service Feign 调用）。
 * 营销服务通过此 DTO 委托交易服务创建秒杀订单。
 */
@Data
public class CreateOrderInternalReq implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull private Long userId;
    @NotNull private Long skuId;
    @NotNull private Long sellerId;
    @NotNull private Integer quantity;
    @NotNull private Integer flashPrice;   // 秒杀价(分)
    @NotNull private Long flashId;         // 秒杀活动ID
    @NotNull private Long addressId;

    /** SKU 快照数据（从 item-service 查询后传入，避免 trade-service 重复查询） */
    private String productName;
    private String skuSpec;
    private String image;
}
