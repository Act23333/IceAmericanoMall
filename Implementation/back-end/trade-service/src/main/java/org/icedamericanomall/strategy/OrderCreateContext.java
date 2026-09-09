package org.icedamericanomall.strategy;

import lombok.Data;
import org.icedamericanomall.dto.AddressDTO;
import org.icedamericanomall.dto.CartItemDTO;
import org.icedamericanomall.enums.OrderTypeEnum;

import java.util.List;

/**
 * V4.1: 订单创建上下文（京东/淘宝 OrderContext 模式）。
 * 承载所有订单创建所需数据，在策略执行过程中被逐步填充。
 */
@Data
public class OrderCreateContext {

    // === 基础信息 ===
    private Long userId;
    private OrderTypeEnum orderType;

    // === 购物车下单 ===
    private List<CartItemDTO> cartItems;

    // === 立即购买 / 秒杀 ===
    private Long skuId;
    private Integer quantity;

    // === 秒杀专有（由 marketing-service Feign 调用传入） ===
    private Long flashId;
    private Integer flashPrice;   // 秒杀价(分)

    // === 通用 ===
    private Long addressId;
    private Long userCouponId;
    private AddressDTO address;   // 策略校验时填充

    // === 计算结果（策略 buildOrder 填充） ===
    private List<CartItemSnapshot> snapshots;
    private Long sellerId;
    private int totalAmount;
    private int discountAmount;

    @Data
    public static class CartItemSnapshot {
        private Long skuId;
        private Long sellerId;
        private String productName;
        private String skuSpec;
        private Integer price;
        private Integer quantity;
        private String image;
    }
}
