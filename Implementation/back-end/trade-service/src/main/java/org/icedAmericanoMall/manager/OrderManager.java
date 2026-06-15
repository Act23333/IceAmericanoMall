package org.icedAmericanoMall.manager;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.client.AddressClient;
import org.icedAmericanoMall.client.CartClient;
import org.icedAmericanoMall.client.SkuClient;
import org.icedAmericanoMall.convert.OrderConverter;
import org.icedAmericanoMall.domain.dto.CreateOrderReq;
import org.icedAmericanoMall.domain.entity.OrderEntity;
import org.icedAmericanoMall.domain.entity.OrderItemEntity;
import org.icedAmericanoMall.domain.vo.OrderVO;
import org.icedAmericanoMall.dto.AddressDTO;
import org.icedAmericanoMall.dto.CartItemDTO;
import org.icedAmericanoMall.dto.SkuDTO;
import org.icedAmericanoMall.dto.StockOpDTO;
import org.icedAmericanoMall.enums.OrderStatusEnum;
import org.icedAmericanoMall.service.OrderService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OrderManager — orchestrates the order creation flow with real Feign integration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderManager {

    private final OrderService orderService;
    private final OrderConverter orderConverter;
    private final CartClient cartClient;
    private final AddressClient addressClient;
    private final SkuClient skuClient;

    /**
     * Create an order from the user's selected cart items.
     * Full Feign-orchestrated flow:
     * 1. Fetch selected cart items from cart-service
     * 2. Enrich with SKU details from item-service
     * 3. Snapshot address from user-service
     * 4. Deduct stock via item-service (before local TX)
     * 5. Create order + items in local DB
     * 6. Clear cart via cart-service
     * 7. Compensate (restore stock) if local TX fails
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(Long userId, CreateOrderReq req) {

        // 1. Fetch selected cart items via Feign
        List<CartItemDTO> cartItems = cartClient.getSelectedItems(userId);
        if (cartItems == null || cartItems.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "购物车为空，无法下单");
        }

        // 2. Enrich with SKU details (product name, spec, image, price, sellerId) via Feign
        List<Long> skuIds = cartItems.stream().map(CartItemDTO::getSkuId).collect(Collectors.toList());
        List<SkuDTO> skuList = skuClient.getSkuListByIds(skuIds);
        if (skuList == null || skuList.size() != skuIds.size()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "商品信息异常，请刷新后重试");
        }
        Map<Long, SkuDTO> skuMap = skuList.stream()
                .collect(Collectors.toMap(SkuDTO::getSkuId, s -> s, (a, b) -> a));

        // 3. Build CartItemSnapshot list (merged cart + SKU data)
        List<CartItemSnapshot> snapshots = new ArrayList<>();
        Long sellerId = null;
        for (CartItemDTO cartItem : cartItems) {
            SkuDTO sku = skuMap.get(cartItem.getSkuId());
            if (sku == null) {
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                        "SKU不存在: " + cartItem.getSkuId());
            }
            if (sku.getStock() < cartItem.getQuantity()) {
                throw new BizException(ErrorCode.BALANCE_INSUFFICIENT,
                        "商品 [" + sku.getProductName() + "] 库存不足");
            }
            // All cart items must be from the same seller for MVP
            if (sellerId == null) {
                sellerId = sku.getSellerId();
            } else if (!sellerId.equals(sku.getSellerId())) {
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                        "暂不支持跨店下单，请分别结算");
            }

            CartItemSnapshot snapshot = new CartItemSnapshot();
            snapshot.setSkuId(cartItem.getSkuId());
            snapshot.setSellerId(sku.getSellerId());
            snapshot.setProductName(sku.getProductName());
            snapshot.setSkuSpec(sku.getSpec());
            snapshot.setPrice(sku.getPrice());
            snapshot.setQuantity(cartItem.getQuantity());
            snapshot.setImage(sku.getImage());
            snapshots.add(snapshot);
        }

        // 4. Snapshot address via Feign
        AddressDTO address = addressClient.getAddress(req.getAddressId());
        if (address == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "收货地址不存在");
        }

        // 5. Build order entity
        OrderEntity order = new OrderEntity();
        order.setOrderNo(IdUtil.fastSimpleUUID());
        order.setUserId(userId);
        order.setSellerId(sellerId);
        order.setStatus(OrderStatusEnum.PENDING_PAYMENT.getCode());
        order.setDiscountAmount(0);

        // 6. Calculate totals and build order items
        int totalAmount = 0;
        List<OrderItemEntity> items = new ArrayList<>();
        List<StockOpDTO> stockOps = new ArrayList<>();
        for (CartItemSnapshot snap : snapshots) {
            OrderItemEntity item = new OrderItemEntity();
            item.setSkuId(snap.getSkuId());
            item.setProductName(snap.getProductName());
            item.setSkuSpec(snap.getSkuSpec());
            item.setPrice(snap.getPrice());
            item.setQuantity(snap.getQuantity());
            item.setSubTotal(snap.getPrice() * snap.getQuantity());
            item.setImage(snap.getImage());
            items.add(item);
            totalAmount += item.getSubTotal();

            StockOpDTO stockOp = new StockOpDTO();
            stockOp.setSkuId(snap.getSkuId());
            stockOp.setQuantity(snap.getQuantity());
            stockOps.add(stockOp);
        }

        order.setTotalAmount(totalAmount);
        order.setPayAmount(totalAmount);

        // Snapshot address
        order.setReceiverName(address.getReceiver());
        order.setReceiverPhone(address.getPhone());
        order.setReceiverAddress(
                address.getProvince() + address.getCity() + address.getDistrict()
                        + address.getStreet() + address.getDetail());

        // 7. Deduct stock BEFORE local transaction (compensation on failure)
        try {
            skuClient.deductStock(stockOps);
        } catch (Exception e) {
            log.error("库存扣减失败", e);
            throw new BizException(ErrorCode.BALANCE_INSUFFICIENT, "库存扣减失败，请重试");
        }

        // 8. Create order + items in local DB
        try {
            orderService.createOrderWithItems(order, items);
        } catch (Exception e) {
            // Compensate: restore stock
            log.error("订单创建失败，回滚库存", e);
            try {
                skuClient.restoreStock(stockOps);
            } catch (Exception restoreEx) {
                log.error("库存回滚失败！需人工处理: stockOps={}", stockOps, restoreEx);
            }
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "订单创建失败");
        }

        // 9. Clear cart after successful order
        try {
            cartClient.clearCart(userId);
        } catch (Exception e) {
            // Non-critical: cart will be cleaned up on next order attempt
            log.error("清空购物车失败（非致命）: userId={}", userId, e);
        }

        // 10. Build response
        OrderVO vo = orderConverter.entityToVO(order);
        vo.setItems(orderConverter.itemEntitiesToVOs(items));
        return vo;
    }

    /**
     * Snapshot of a cart item enriched with SKU details from item-service.
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
