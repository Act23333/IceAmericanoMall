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
    private Long userCouponId;            // @Deprecated V4.3: 单券，保留兼容
    private List<Long> userCouponIds;     // V4.3: 多券支持
    private AddressDTO address;

    // V4.3: 订单商品信息（用于优惠券 scope 校验）
    private String productIds;            // SKU对应的 productIds，逗号分隔
    private String categoryIds;           // SKU对应的 categoryIds，逗号分隔

    // === 计算结果（策略 buildOrder 填充） ===
    private List<CartItemSnapshot> snapshots;
    private Long sellerId;
    private int totalAmount;
    private int discountAmount;

    @Data
    public static class CartItemSnapshot {
        private Long skuId;
        private Long productId;    // V4.3: 优惠券 scope 校验用
        private Long categoryId;   // V4.3: 优惠券 scope 校验用
        private Long sellerId;
        private String productName;
        private String skuSpec;
        private Integer price;
        private Integer quantity;
        private String image;
    }
}
