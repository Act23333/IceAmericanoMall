package org.icedamericanomall.trade.app.order.strategy;

import lombok.Data;
import org.icedamericanomall.dto.AddressCO;
import org.icedamericanomall.dto.CartItemCO;
import org.icedamericanomall.trade.domain.enums.OrderTypeEnum;

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
    private List<CartItemCO> cartItems;

    // === 立即购买 / 秒杀 ===
    private Long skuId;
    private Integer quantity;

    // === 秒杀专有（由 marketing-service Feign 调用传入） ===
    private Long flashId;
    private Integer flashPrice;   // 秒杀价(分)

    // === 通用 ===
    private Long addressId;
    /** 买家留言（V5.7，可选，≤200）—— 随 {@code buildBaseOrder} 快照进订单聚合。 */
    private String remark;
    private Long userCouponId;            // @Deprecated V4.3: 单券，保留兼容
    private List<Long> userCouponIds;     // V4.3: 多券支持
    private AddressCO address;

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
        /** V5.6（F2）：商品绑定的运费模板 ID（可为 null = 未绑定 → 计价回落商家默认模板）。 */
        private Long freightTemplateId;
    }
}
