package org.icedAmericanoMall.manager;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.convert.OrderConverter;
import org.icedAmericanoMall.domain.dto.CreateOrderReq;
import org.icedAmericanoMall.domain.entity.OrderEntity;
import org.icedAmericanoMall.domain.entity.OrderItemEntity;
import org.icedAmericanoMall.domain.vo.OrderVO;
import org.icedAmericanoMall.enums.OrderStatusEnum;
import org.icedAmericanoMall.service.OrderService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * OrderManager — orchestrates the order creation flow.
 *
 * In a full deployment this would:
 * 1. Query selected cart items via Feign (cart-service)
 * 2. Fetch SKU details via Feign (item-service)
 * 3. Deduct stock via Feign (item-service)
 * 4. Snapshot address via Feign (user-service)
 * 5. Clear cart via Feign (cart-service)
 *
 * For MVP, Feign integration is prepared but the core flow works with in-DB data.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderManager {

    private final OrderService orderService;
    private final OrderConverter orderConverter;

    /**
     * Create an order from selected cart items.
     * In full deployment, {@code cartItems} would be enriched with SKU details
     * fetched from item-service via SkuClient.
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(Long userId, CreateOrderReq req,
                                List<CartItemSnapshot> cartItems) {
        if (cartItems == null || cartItems.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "购物车为空，无法下单");
        }

        // Build order entity
        OrderEntity order = new OrderEntity();
        order.setOrderNo(IdUtil.fastSimpleUUID());
        order.setUserId(userId);
        order.setSellerId(cartItems.get(0).getSellerId());
        order.setStatus(OrderStatusEnum.PENDING_PAYMENT.getCode());
        order.setDiscountAmount(0);

        // Calculate totals (all in cents)
        int totalAmount = 0;
        List<OrderItemEntity> items = new ArrayList<>();
        for (CartItemSnapshot cartItem : cartItems) {
            OrderItemEntity item = new OrderItemEntity();
            item.setSkuId(cartItem.getSkuId());
            item.setProductName(cartItem.getProductName());
            item.setSkuSpec(cartItem.getSkuSpec());
            item.setPrice(cartItem.getPrice());
            item.setQuantity(cartItem.getQuantity());
            item.setSubTotal(cartItem.getPrice() * cartItem.getQuantity());
            item.setImage(cartItem.getImage());
            items.add(item);
            totalAmount += item.getSubTotal();
        }

        order.setTotalAmount(totalAmount);
        order.setPayAmount(totalAmount);

        // Snapshot address (placeholder — real impl fetches from user-service)
        order.setReceiverName("");
        order.setReceiverPhone("");
        order.setReceiverAddress("");

        // Create order + items atomically
        orderService.createOrderWithItems(order, items);

        OrderVO vo = orderConverter.entityToVO(order);
        vo.setItems(orderConverter.itemEntitiesToVOs(items));
        return vo;
    }

    /**
     * Snapshot of a cart item enriched with SKU details.
     * In production this data comes from SkuClient + CartService Feign calls.
     */
    @lombok.Data
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
