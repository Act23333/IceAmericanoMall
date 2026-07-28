package org.icedamericanomall.manager;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.AddressClient;
import org.icedamericanomall.client.CartClient;
import org.icedamericanomall.client.CouponClient;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.convert.OrderConverter;
import org.icedamericanomall.domain.dto.CreateOrderReq;
import org.icedamericanomall.domain.dto.DirectOrderReq;
import org.icedamericanomall.domain.entity.OrderEntity;
import org.icedamericanomall.domain.entity.OrderItemEntity;
import org.icedamericanomall.domain.vo.OrderVO;
import org.icedamericanomall.dto.AddressDTO;
import org.icedamericanomall.dto.CartItemDTO;
import org.icedamericanomall.dto.SkuDTO;
import org.icedamericanomall.dto.StockOpDTO;
import org.icedamericanomall.enums.OrderStatusEnum;
import org.icedamericanomall.producer.OrderTimeoutPublisher;
import org.icedamericanomall.service.OrderService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单创建编排 Manager —— 下单主流程：购物车→SKU→地址→库存→优惠券→订单。
 * 订单取消/收货/发货/超时等生命周期操作已拆分至 {@link OrderLifecycleManager}。
 *
 * <pre>
 * Scenario: 从购物车成功下单
 * Scenario: 库存不足 / 跨店 / 空购物车 时拒绝
 * Scenario: 订单创建失败：Saga 补偿回滚库存 + 已用优惠券
 * </pre>
 *
 * V4.0: 提取共享 buildOrderAndSave()，修复所有 buildOrder 缺陷（sellerId/优惠券/状态枚举/地址/持久化）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreationManager {

    private final OrderService orderService;
    private final OrderConverter orderConverter;
    private final CartClient cartClient;
    private final AddressClient addressClient;
    private final SkuClient skuClient;
    private final CouponClient couponClient;
    private final OrderTimeoutPublisher timeoutPublisher;

    /**
     * 购物车下单：从购物车获取选中商品 → 校验 → 扣库存 → 创建订单 → 清除已下单的购物车项。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(Long userId, CreateOrderReq req) {

        // Given: 从购物车服务获取用户选中的商品
        List<CartItemDTO> cartItems = cartClient.getSelectedItems(userId);
        if (cartItems == null || cartItems.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "购物车为空，无法下单");
        }

        // When: Feign 获取 SKU 详情
        List<Long> skuIds = cartItems.stream().map(CartItemDTO::getSkuId).collect(Collectors.toList());
        List<SkuDTO> skuList = skuClient.getSkuListByIds(skuIds);
        if (skuList == null || skuList.size() != skuIds.size()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "商品信息异常，请刷新后重试");
        }
        Map<Long, SkuDTO> skuMap = skuList.stream()
                .collect(Collectors.toMap(SkuDTO::getId, s -> s, (a, b) -> a));

        // Then: 合并购物车与 SKU，校验库存 + 单店铺，构建快照
        List<CartItemSnapshot> snapshots = new ArrayList<>();
        Long sellerId = null;
        for (CartItemDTO cartItem : cartItems) {
            SkuDTO sku = skuMap.get(cartItem.getSkuId());
            if (sku == null) {
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "SKU不存在: " + cartItem.getSkuId());
            }
            // V4.0: 仅限量商品校验库存
            if (sku.getStockType() == null || sku.getStockType() == 1) { // LIMITED or null (backward compat)
                if (sku.getStock() < cartItem.getQuantity()) {
                    throw new BizException(ErrorCode.BALANCE_INSUFFICIENT,
                            "商品 [" + sku.getProductName() + "] 库存不足");
                }
            }
            if (sellerId == null) sellerId = sku.getSellerId();
            else if (!sellerId.equals(sku.getSellerId())) {
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "暂不支持跨店下单，请分别结算");
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

        // When: Feign 获取收货地址快照
        AddressDTO address = addressClient.getAddress(req.getAddressId());
        if (address == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "收货地址不存在");
        }

        // Then: 统一创建订单（共享方法，含所有 bug 修复）
        OrderVO vo = buildOrderAndSave(userId, sellerId, address, snapshots, req.getUserCouponId());

        // V4.0: 仅删除已下单的购物车项（非全量清空，京东标准）
        try {
            List<Long> orderedCartItemIds = cartItems.stream()
                    .map(CartItemDTO::getCartItemId)
                    .filter(id -> id != null)
                    .collect(Collectors.toList());
            if (!orderedCartItemIds.isEmpty()) {
                cartClient.deleteByIds(userId, orderedCartItemIds);
            }
        } catch (Exception e) {
            log.error("删除已下单购物车项失败（非致命）: userId={}, orderNo={}", userId, vo.getOrderNo(), e);
        }

        return vo;
    }

    /**
     * V4.0: 立即购买 — 跳过购物车，直接下单（京东标准）。
     * 与 createOrder() 的区别: 不读取购物车、不操作购物车。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrderDirect(Long userId, DirectOrderReq req) {
        // 1. 查 SKU 详情
        List<SkuDTO> skuList = skuClient.getSkuListByIds(List.of(req.getSkuId()));
        if (skuList == null || skuList.isEmpty())
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "SKU不存在");

        SkuDTO sku = skuList.get(0);
        // V4.0: 仅限量商品校验库存
        if (sku.getStockType() == null || sku.getStockType() == 1) {
            if (sku.getStock() < req.getQuantity())
                throw new BizException(ErrorCode.STOCK_INSUFFICIENT);
        }

        // 2. 验证地址
        var addr = addressClient.getAddress(req.getAddressId());
        if (addr == null) throw new BizException(ErrorCode.PARAM_ERROR, "收货地址不存在");

        // 3. 构建单个 CartItemSnapshot（模拟购物车单商品）
        CartItemSnapshot snap = new CartItemSnapshot();
        snap.setSkuId(req.getSkuId());
        snap.setSellerId(sku.getSellerId());
        snap.setProductName(sku.getProductName());
        snap.setSkuSpec(sku.getSpec());
        snap.setPrice(sku.getPrice());
        snap.setQuantity(req.getQuantity());
        snap.setImage(sku.getImage());

        // 4. V4.0: 立即购买不操作购物车（京东标准：商品详情页直接下单）
        return buildOrderAndSave(userId, sku.getSellerId(), addr, List.of(snap), req.getUserCouponId());
    }

    /**
     * V4.0: 共享订单构建 + 持久化方法（购物车下单和立即购买共用）。
     * 修复了 V4.0 之前的全部 5 个缺陷:
     * 1. ✅ sellerId 正确设置
     * 2. ✅ 优惠券抵扣生效
     * 3. ✅ 使用 OrderStatusEnum 枚举，非硬编码字面量
     * 4. ✅ 收货地址包含 detail 字段
     * 5. ✅ 订单项批量持久化
     */
    private OrderVO buildOrderAndSave(Long userId, Long sellerId,
                                       AddressDTO address,
                                       List<CartItemSnapshot> snapshots,
                                       Long userCouponId) {
        // Given: 构建订单实体 + 计算金额
        OrderEntity order = new OrderEntity();
        order.setOrderNo(IdUtil.fastSimpleUUID());
        order.setUserId(userId);
        order.setSellerId(sellerId);                        // FIX 1: V4.0 之前 buildOrder 遗漏
        order.setStatus(OrderStatusEnum.PENDING_PAYMENT.getCode()); // FIX 3: 使用枚举

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

        // FIX 2: V4.0 使用优惠券抵扣（之前 buildOrder 忽略此参数）
        int discount = 0;
        if (userCouponId != null) {
            Integer applied = couponClient.useCoupon(
                    userId, userCouponId, order.getOrderNo(), totalAmount);
            discount = applied != null ? Math.min(applied, totalAmount) : 0;
        }
        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(discount);
        order.setPayAmount(totalAmount - discount);

        // FIX 4: V4.0 收货地址包含 detail 字段
        order.setReceiverName(address.getReceiver());
        order.setReceiverPhone(address.getPhone());
        order.setReceiverAddress(
                address.getProvince() + address.getCity() + address.getDistrict()
                        + address.getStreet() + address.getDetail());

        // When: 先扣减库存（远程调用）
        try {
            skuClient.deductStock(stockOps);
        } catch (Exception e) {
            log.error("库存扣减失败", e);
            throw new BizException(ErrorCode.BALANCE_INSUFFICIENT, "库存扣减失败，请重试");
        }

        // Then: 本地事务创建订单 + 订单项（FIX 5: items 批量持久化在 OrderServiceImpl 中修复）
        try {
            orderService.createOrderWithItems(order, items);
            publishTimeoutAfterCommit(order.getOrderNo());
        } catch (Exception e) {
            // Saga 补偿：回滚库存 + 已用优惠券
            log.error("订单创建失败，回滚库存", e);
            try {
                skuClient.restoreStock(stockOps);
            } catch (Exception re) {
                log.error("库存回滚失败！需人工处理: stockOps={}", stockOps, re);
            }
            if (discount > 0) {
                try {
                    couponClient.rollbackByOrderNo(order.getOrderNo());
                } catch (Exception ce) {
                    log.error("优惠券回滚失败！需人工处理: orderNo={}", order.getOrderNo(), ce);
                }
            }
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "订单创建失败");
        }

        // Then: 返回订单 VO
        OrderVO vo = orderConverter.entityToVO(order);
        vo.setItems(orderConverter.itemEntitiesToVOs(items));
        return vo;
    }

    private void publishTimeoutAfterCommit(String orderNo) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            timeoutPublisher.publishTimeout(orderNo);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                timeoutPublisher.publishTimeout(orderNo);
            }
        });
    }

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
