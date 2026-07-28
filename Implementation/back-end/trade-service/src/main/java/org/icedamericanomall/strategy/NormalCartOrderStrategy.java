package org.icedamericanomall.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.AddressClient;
import org.icedamericanomall.client.CartClient;
import org.icedamericanomall.client.CouponClient;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.domain.entity.OrderEntity;
import org.icedamericanomall.domain.entity.OrderItemEntity;
import org.icedamericanomall.dto.AddressDTO;
import org.icedamericanomall.dto.CartItemDTO;
import org.icedamericanomall.dto.SkuDTO;
import org.icedamericanomall.enums.OrderStatusEnum;
import org.icedamericanomall.enums.OrderTypeEnum;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * V4.1: 购物车下单策略。
 * 校验: 购物车非空 → SKU批量查询 → 限量库存检查 → 单店铺校验 → 地址快照
 * 后置: 删除已购购物车项
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NormalCartOrderStrategy implements OrderCreateStrategy {

    private final CartClient cartClient;
    private final SkuClient skuClient;
    private final AddressClient addressClient;
    private final CouponClient couponClient;

    @Override
    public OrderTypeEnum supportedType() {
        return OrderTypeEnum.NORMAL;
    }

    @Override
    public void validate(OrderCreateContext ctx) {
        List<CartItemDTO> cartItems = cartClient.getSelectedItems(ctx.getUserId());
        if (cartItems == null || cartItems.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "购物车为空，无法下单");
        }
        ctx.setCartItems(cartItems);

        List<Long> skuIds = cartItems.stream().map(CartItemDTO::getSkuId).collect(Collectors.toList());
        List<SkuDTO> skuList = skuClient.getSkuListByIds(skuIds);
        if (skuList == null || skuList.size() != skuIds.size()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "商品信息异常，请刷新后重试");
        }
        Map<Long, SkuDTO> skuMap = skuList.stream()
                .collect(Collectors.toMap(SkuDTO::getId, s -> s, (a, b) -> a));

        List<OrderCreateContext.CartItemSnapshot> snapshots = new ArrayList<>();
        Long sellerId = null;
        for (CartItemDTO cartItem : cartItems) {
            SkuDTO sku = skuMap.get(cartItem.getSkuId());
            if (sku == null) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                    "SKU不存在: " + cartItem.getSkuId());
            if (sku.getStockType() == null || sku.getStockType() == 1) {
                if (sku.getStock() < cartItem.getQuantity()) {
                    throw new BizException(ErrorCode.BALANCE_INSUFFICIENT,
                            "商品 [" + sku.getProductName() + "] 库存不足");
                }
            }
            if (sellerId == null) sellerId = sku.getSellerId();
            else if (!sellerId.equals(sku.getSellerId())) {
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "暂不支持跨店下单，请分别结算");
            }
            OrderCreateContext.CartItemSnapshot snap = new OrderCreateContext.CartItemSnapshot();
            snap.setSkuId(cartItem.getSkuId());
            snap.setSellerId(sku.getSellerId());
            snap.setProductName(sku.getProductName());
            snap.setSkuSpec(sku.getSpec());
            snap.setPrice(sku.getPrice());
            snap.setQuantity(cartItem.getQuantity());
            snap.setImage(sku.getImage());
            snapshots.add(snap);
        }
        ctx.setSnapshots(snapshots);
        ctx.setSellerId(sellerId);

        AddressDTO address = addressClient.getAddress(ctx.getAddressId());
        if (address == null) throw new BizException(ErrorCode.PARAM_ERROR, "收货地址不存在");
        ctx.setAddress(address);
    }

    @Override
    public OrderEntity buildOrder(OrderCreateContext ctx) {
        OrderEntity order = buildBaseOrder(ctx);
        order.setOrderType(OrderTypeEnum.NORMAL.getCode());

        int total = ctx.getTotalAmount();
        int discount = 0;
        if (ctx.getUserCouponId() != null) {
            Integer applied = couponClient.useCoupon(
                    ctx.getUserId(), ctx.getUserCouponId(), order.getOrderNo(), total);
            discount = applied != null ? Math.min(applied, total) : 0;
        }
        order.setTotalAmount(total);
        order.setDiscountAmount(discount);
        order.setPayAmount(total - discount);
        ctx.setDiscountAmount(discount);

        fillAddress(ctx, order);
        return order;
    }

    @Override
    public void afterCreate(OrderCreateContext ctx, OrderEntity order) {
        List<CartItemDTO> cartItems = ctx.getCartItems();
        if (cartItems != null && !cartItems.isEmpty()) {
            try {
                List<Long> orderedIds = cartItems.stream()
                        .map(CartItemDTO::getCartItemId).filter(id -> id != null).toList();
                if (!orderedIds.isEmpty()) cartClient.deleteByIds(ctx.getUserId(), orderedIds);
            } catch (Exception e) {
                log.error("删除已购购物车项失败（非致命）: userId={}, orderNo={}", ctx.getUserId(),
                        order.getOrderNo(), e);
            }
        }
    }

    // === Shared helpers used by all strategies ===

    static OrderEntity buildBaseOrder(OrderCreateContext ctx) {
        OrderEntity order = new OrderEntity();
        order.setOrderNo(cn.hutool.core.util.IdUtil.fastSimpleUUID());
        order.setUserId(ctx.getUserId());
        order.setSellerId(ctx.getSellerId());
        order.setStatus(OrderStatusEnum.PENDING_PAYMENT.getCode());
        int totalAmount = 0;
        for (var snap : ctx.getSnapshots()) {
            totalAmount += snap.getPrice() * snap.getQuantity();
        }
        ctx.setTotalAmount(totalAmount);
        return order;
    }

    static void fillAddress(OrderCreateContext ctx, OrderEntity order) {
        AddressDTO addr = ctx.getAddress();
        order.setReceiverName(addr.getReceiver());
        order.setReceiverPhone(addr.getPhone());
        order.setReceiverAddress(addr.getProvince() + addr.getCity() + addr.getDistrict()
                + addr.getStreet() + addr.getDetail());
    }

    /** Build item entities from snapshots (orderId set later by OrderServiceImpl). */
    public static List<OrderItemEntity> buildItems(OrderEntity order, OrderCreateContext ctx) {
        List<OrderItemEntity> items = new ArrayList<>();
        for (var snap : ctx.getSnapshots()) {
            OrderItemEntity item = new OrderItemEntity();
            item.setSkuId(snap.getSkuId());
            item.setProductName(snap.getProductName());
            item.setSkuSpec(snap.getSkuSpec());
            item.setPrice(snap.getPrice());
            item.setQuantity(snap.getQuantity());
            item.setSubTotal(snap.getPrice() * snap.getQuantity());
            item.setImage(snap.getImage());
            items.add(item);
        }
        return items;
    }
}
