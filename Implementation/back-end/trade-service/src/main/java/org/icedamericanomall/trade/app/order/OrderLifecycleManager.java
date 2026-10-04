package org.icedamericanomall.trade.app.order;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.CouponClient;
import org.icedamericanomall.client.LogisticsClient;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.client.PointsClient;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.model.order.OrderItem;
import org.icedamericanomall.dto.CreateLogisticsCO;
import org.icedamericanomall.dto.StockOpCO;
import org.icedamericanomall.trade.infrastructure.messaging.DomainEventProducer;
import org.icedamericanomall.trade.domain.repository.OrderRepository;
import org.icedamericanomall.trade.domain.model.order.ShipPackageResult;
import org.icedamericanomall.common.event.OrderShippedEvent;
import org.springframework.stereotype.Component;

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
public class OrderLifecycleManager {

    private static final int POINTS_RATE_DIVISOR = 100;
    private static final int POINTS_TYPE_ORDER_REWARD = 2;

    private final OrderRepository orderRepository;
    private final SkuClient skuClient;
    private final CouponClient couponClient;
    private final LogisticsClient logisticsClient;
    private final PointsClient pointsClient;
    private final DomainEventProducer eventProducer;

    public OrderLifecycleManager(OrderRepository orderRepository, SkuClient skuClient,
                                  CouponClient couponClient, LogisticsClient logisticsClient,
                                  PointsClient pointsClient, DomainEventProducer eventProducer) {
        this.orderRepository = orderRepository;
        this.skuClient = skuClient;
        this.couponClient = couponClient;
        this.logisticsClient = logisticsClient;
        this.pointsClient = pointsClient;
        this.eventProducer = eventProducer;
    }

    public void cancelOrder(String orderNo, Long userId) {
        orderRepository.cancelByUser(orderNo, userId);
        Order order = orderRepository.findByOrderNo(orderNo);
        if (order != null) {
            restoreStock(order.getId());
            rollbackCoupon(order);
        }
    }

    public void confirmReceipt(String orderNo, Long userId) {
        orderRepository.confirmReceiptByUser(orderNo, userId);
        Order order = orderRepository.findByOrderNo(orderNo);
        if (order == null || order.getPayAmount() == null) return;
        int points = order.getPayAmount() / POINTS_RATE_DIVISOR;
        if (points <= 0) return;
        try {
            pointsClient.addPoints(order.getUserId(), points, POINTS_TYPE_ORDER_REWARD, "下单奖励");
        } catch (Exception e) {
            log.error("下单奖励积分发放失败: orderNo={}", orderNo, e);
        }
    }

    /**
     * 商家发货（V5.6 拆单发货，F3）—— 本地事务（明细打包裹号 + 订单状态迁移）在 service 层完成，
     * 本方法在其<b>返回后</b>于事务外做两件非本地库动作：创建物流记录（Feign，fail-open 记日志）、
     * 发布 order.shipped 事件（MQ，非关键路径）。
     *
     * <p>Feign 严禁进事务（硬约束），故「发货事实落库」与「物流记录创建」两层分离：
     * 后者失败不回滚前者（{@code order_logistics} 非事实源，可由对账/重试补齐）。
     *
     * @param orderNo          订单号
     * @param sellerId         商家 ID
     * @param logisticsNumber  物流单号
     * @param logisticsCompany 物流公司
     * @param skuIds           本包裹发货明细；空 = 全部剩余明细（单包裹语义兼容）
     */
    public void shipOrder(String orderNo, Long sellerId, String logisticsNumber, String logisticsCompany,
                          List<Long> skuIds) {
        ShipPackageResult result = orderRepository.shipPackage(orderNo, sellerId, skuIds);
        Order order = orderRepository.findByOrderNo(orderNo);
        if (order == null) {
            log.warn("发货后订单不可见（事务外读取），跳过物流创建: orderNo={}", orderNo);
            return;
        }
        CreateLogisticsCO dto = new CreateLogisticsCO();
        dto.setOrderId(result.orderId());
        // 包裹号 = 本单已发包裹数 + 1（由领域服务在事务内算出，保证与 order_item.package_no 一致）
        dto.setPackageNo(result.packageNo());
        dto.setLogisticsNumber(logisticsNumber);
        dto.setLogisticsCompany(logisticsCompany);
        dto.setContact(order.getReceiverName());
        dto.setMobile(order.getReceiverPhone());
        try {
            logisticsClient.createLogistics(dto);
            log.info("物流记录创建成功: orderId={}, packageNo={}", result.orderId(), result.packageNo());
        } catch (Exception e) {
            log.error("物流记录创建失败，需人工处理: orderId={}, packageNo={}",
                    result.orderId(), result.packageNo(), e);
        }
        // RocketMQ: 发布订单发货事件 (非关键路径，失败不影响主流程)
        try {
            eventProducer.publishOrderShipped(new OrderShippedEvent(
                    orderNo, order.getUserId(), logisticsNumber, logisticsCompany));
        } catch (Exception e) {
            log.warn("订单发货事件发布失败(order.shipped): orderNo={}", orderNo, e);
        }
    }


    /**
     * 支付结果回写（pay-service 内部接口调用）—— 委托仓储条件迁移，
     * 仅允许「待付款 → 待发货」与「待付款 → 已取消」两条合法边。
     */
    public void transitionFromPendingPayment(String orderNo, Integer targetStatus) {
        orderRepository.transitionFromPendingPayment(orderNo, targetStatus);
    }

    /**
     * V3.6: 安全取消订单 —— 幂等 + 并发安全。
     * <p>
     * 状态迁移由 {@code OrderRepository#closeTimeoutOrder(Long)} 以条件更新完成
     * （WHERE id=? AND status=待付款）；只有真正命中状态迁移（返回 true）才回滚库存与优惠券，
     * 避免并发/重复超时消息导致的「双重回滚库存」。
     *
     * @return true=本次真正取消成功；false=订单不存在或已被支付/取消（幂等无操作）
     */
    public boolean cancelOrderSafely(String orderNo, Long userId) {
        Order order = orderRepository.findByOrderNo(orderNo);
        if (order == null) {
            return false;
        }
        boolean cancelled = orderRepository.closeTimeoutOrder(order.getId());
        if (!cancelled) {
            log.info("订单无需取消（状态已变更或不存在）: orderNo={}", orderNo);
            return false;
        }
        restoreStock(order.getId());
        rollbackCoupon(order);
        return true;
    }

    private void restoreStock(Long orderId) {
        List<OrderItem> items = orderRepository.findItemsByOrderId(orderId);
        if (items.isEmpty()) return;
        List<StockOpCO> stockOps = items.stream().map(item -> {
            StockOpCO op = new StockOpCO();
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

    private void rollbackCoupon(Order order) {
        if (order.getDiscountAmount() == null || order.getDiscountAmount() <= 0) return;
        try {
            couponClient.rollbackByOrderNo(order.getOrderNo());
        } catch (Exception e) {
            log.error("优惠券回滚失败，需人工处理: orderNo={}", order.getOrderNo(), e);
        }
    }
}
