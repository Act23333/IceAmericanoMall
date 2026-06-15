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
     * 创建订单 —— 从购物车到生成订单的完整编排。
     *
     * <pre>
     * Scenario: 用户从购物车成功下单
     *   Given 用户已登录，购物车中有选中的商品
     *   And 收货地址已选择
     *   When 用户提交订单
     *   Then 从购物车服务获取选中商品
     *   And 从商品服务获取 SKU 详情并校验库存
     *   And 从用户服务获取收货地址快照
     *   And 扣减对应 SKU 库存
     *   And 创建订单及订单项，状态为"待付款"
     *   And 清空购物车中已下单商品
     *   And 返回订单详情
     *
     * Scenario: 库存不足时拒绝下单
     *   Given SKU 实际库存为 3
     *   And 用户购物车中该 SKU 数量为 5
     *   When 用户提交订单
     *   Then 抛出 BizException "库存不足"
     *
     * Scenario: 订单创建失败时回滚库存（补偿事务）
     *   Given 库存已成功扣减
     *   When 本地订单写入数据库失败
     *   Then 调用 restoreStock 补偿恢复库存
     *   And 抛出 BizException "订单创建失败"
     * </pre>
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(Long userId, CreateOrderReq req) {

        // Given: 从购物车服务获取用户选中的商品
        List<CartItemDTO> cartItems = cartClient.getSelectedItems(userId);
        // Given: 购物车不能为空
        if (cartItems == null || cartItems.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "购物车为空，无法下单");
        }

        // When: 通过 Feign 从商品服务获取 SKU 详情（商品名、规格、价格、图片、所属商家）
        List<Long> skuIds = cartItems.stream().map(CartItemDTO::getSkuId).collect(Collectors.toList());
        List<SkuDTO> skuList = skuClient.getSkuListByIds(skuIds);
        if (skuList == null || skuList.size() != skuIds.size()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "商品信息异常，请刷新后重试");
        }
        Map<Long, SkuDTO> skuMap = skuList.stream()
                .collect(Collectors.toMap(SkuDTO::getSkuId, s -> s, (a, b) -> a));

        // Then: 合并购物车数据与SKU详情，逐个校验库存并构建快照
        List<CartItemSnapshot> snapshots = new ArrayList<>();
        Long sellerId = null;
        for (CartItemDTO cartItem : cartItems) {
            SkuDTO sku = skuMap.get(cartItem.getSkuId());
            if (sku == null) {
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                        "SKU不存在: " + cartItem.getSkuId());
            }
            // Then: 库存不足时拒绝下单
            if (sku.getStock() < cartItem.getQuantity()) {
                throw new BizException(ErrorCode.BALANCE_INSUFFICIENT,
                        "商品 [" + sku.getProductName() + "] 库存不足");
            }
            // Then: MVP阶段仅支持单店铺下单
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

        // When: 通过 Feign 从用户服务获取收货地址快照
        AddressDTO address = addressClient.getAddress(req.getAddressId());
        if (address == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "收货地址不存在");
        }

        // Given: 构建订单实体，生成订单号，计算金额（单位：分）
        OrderEntity order = new OrderEntity();
        order.setOrderNo(IdUtil.fastSimpleUUID());
        order.setUserId(userId);
        order.setSellerId(sellerId);
        order.setStatus(OrderStatusEnum.PENDING_PAYMENT.getCode());
        order.setDiscountAmount(0);

        // Then: 订单总金额 = 各订单项小计之和
        int totalAmount = 0;
        List<OrderItemEntity> items = new ArrayList<>();
        List<StockOpDTO> stockOps = new ArrayList<>();
        for (CartItemSnapshot snap : snapshots) {
            OrderItemEntity item = new OrderItemEntity();
            // 下单时快照商品名、规格、价格、图片（不再引用商品表）
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

        // 收货信息为下单时地址快照
        order.setReceiverName(address.getReceiver());
        order.setReceiverPhone(address.getPhone());
        order.setReceiverAddress(
                address.getProvince() + address.getCity() + address.getDistrict()
                        + address.getStreet() + address.getDetail());

        // When: 先扣减库存（远程调用，不在本地事务内）
        try {
            skuClient.deductStock(stockOps);
        } catch (Exception e) {
            log.error("库存扣减失败", e);
            throw new BizException(ErrorCode.BALANCE_INSUFFICIENT, "库存扣减失败，请重试");
        }

        // Then: 本地事务创建订单 + 订单项
        try {
            orderService.createOrderWithItems(order, items);
        } catch (Exception e) {
            // Then: 订单创建失败时补偿回滚库存（Saga模式）
            log.error("订单创建失败，回滚库存", e);
            try {
                skuClient.restoreStock(stockOps);
            } catch (Exception restoreEx) {
                log.error("库存回滚失败！需人工处理: stockOps={}", stockOps, restoreEx);
            }
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "订单创建失败");
        }

        // Then: 下单成功后清空购物车中已下单项
        try {
            cartClient.clearCart(userId);
        } catch (Exception e) {
            // 清空购物车失败为非致命错误，下次下单时会自动覆盖
            log.error("清空购物车失败（非致命）: userId={}", userId, e);
        }

        // Then: 返回订单VO（含订单项列表）
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
