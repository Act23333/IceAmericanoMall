package org.icedAmericanoMall.manager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.client.CouponClient;
import org.icedAmericanoMall.client.LogisticsClient;
import org.icedAmericanoMall.client.SkuClient;
import org.icedAmericanoMall.client.PointsClient;
import org.icedAmericanoMall.domain.entity.OrderEntity;
import org.icedAmericanoMall.domain.entity.OrderItemEntity;
import org.icedAmericanoMall.dto.CreateLogisticsDTO;
import org.icedAmericanoMall.dto.StockOpDTO;
import org.icedAmericanoMall.service.OrderService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单生命周期编排 Manager —— 取消/确认收货/发货/超时取消，含对应的库存与优惠券补偿。
 * 下单主流程已拆分至 {@link OrderCreationManager}。
 *
 * <pre>
 * Scenario: 用户取消 → 回滚库存 + 优惠券
 * Scenario: 确认收货 → 发放积分
 * Scenario: 商家发货 → 创建物流记录
 * Scenario: 超时未支付 → 逐单取消并回滚
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderLifecycleManager {

    /** 确认收货奖励积分比例：支付金额（分）的 1%。 */
    private static final int POINTS_RATE_DIVISOR = 100;
    private static final int POINTS_TYPE_ORDER_REWARD = 2;
    private static final int ORDER_TIMEOUT_MINUTES = 30;

    private final OrderService orderService;
    private final SkuClient skuClient;
    private final CouponClient couponClient;
    private final LogisticsClient logisticsClient;
    private final PointsClient pointsClient;

    public void cancelOrder(String orderNo, Long userId) {
        orderService.cancelOrder(orderNo, userId);
        OrderEntity order = orderService.getByOrderNo(orderNo);
        if (order != null) {
            restoreStock(order.getId());
            rollbackCoupon(order);
        }
    }

    public void confirmReceipt(String orderNo, Long userId) {
        orderService.confirmReceipt(orderNo, userId);
        OrderEntity order = orderService.getByOrderNo(orderNo);
        if (order == null || order.getPayAmount() == null) return;
        int points = order.getPayAmount() / POINTS_RATE_DIVISOR;
        if (points <= 0) return;
        try {
            pointsClient.addPoints(order.getUserId(), points, POINTS_TYPE_ORDER_REWARD, "下单奖励");
        } catch (Exception e) {
            log.error("下单奖励积分发放失败: orderNo={}", orderNo, e);
        }
    }

    public void shipOrder(String orderNo, Long sellerId, String logisticsNumber, String logisticsCompany) {
        orderService.shipOrder(orderNo, sellerId, logisticsNumber, logisticsCompany);
        OrderEntity order = orderService.getByOrderNo(orderNo);
        if (order == null) return;
        CreateLogisticsDTO dto = new CreateLogisticsDTO();
        dto.setOrderId(order.getId());
        dto.setLogisticsNumber(logisticsNumber);
        dto.setLogisticsCompany(logisticsCompany);
        dto.setContact(order.getReceiverName());
        dto.setMobile(order.getReceiverPhone());
        try {
            logisticsClient.createLogistics(dto);
            log.info("物流记录创建成功: orderId={}", order.getId());
        } catch (Exception e) {
            log.error("物流记录创建失败，需人工处理: orderId={}", order.getId(), e);
        }
    }

    public void cancelTimeoutOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(ORDER_TIMEOUT_MINUTES);
        List<OrderEntity> timeoutOrders = orderService.listTimeoutPending(cutoff);
        for (OrderEntity order : timeoutOrders) {
            try {
                orderService.closeTimeoutOrder(order.getId());
                restoreStock(order.getId());
                rollbackCoupon(order);
                log.info("自动取消超时订单: {}", order.getOrderNo());
            } catch (Exception e) {
                log.error("自动取消订单失败: {}", order.getOrderNo(), e);
            }
        }
    }

    private void restoreStock(Long orderId) {
        List<OrderItemEntity> items = orderService.listItems(orderId);
        if (items.isEmpty()) return;
        List<StockOpDTO> stockOps = items.stream().map(item -> {
            StockOpDTO op = new StockOpDTO();
            op.setSkuId(item.getSkuId());
            op.setQuantity(item.getQuantity());
            return op;
        }).collect(Collectors.toList());
        try {
            skuClient.restoreStock(stockOps);
            log.info("库存已恢复: orderId={}", orderId);
        } catch (Exception e) {
            log.error("库存恢复失败，需人工处理: orderId={}", orderId, e);
        }
    }

    private void rollbackCoupon(OrderEntity order) {
        if (order.getDiscountAmount() == null || order.getDiscountAmount() <= 0) return;
        try {
            couponClient.rollbackByOrderNo(order.getOrderNo());
        } catch (Exception e) {
            log.error("优惠券回滚失败，需人工处理: orderNo={}", order.getOrderNo(), e);
        }
    }
}
