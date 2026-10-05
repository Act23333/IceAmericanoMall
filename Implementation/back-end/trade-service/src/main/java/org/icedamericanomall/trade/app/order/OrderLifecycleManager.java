package org.icedamericanomall.trade.app.order;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.CouponClient;
import org.icedamericanomall.client.LogisticsClient;
import org.icedamericanomall.client.MemberClient;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.client.PointsClient;
import org.icedamericanomall.client.TaskClient;
import org.icedamericanomall.dto.MemberGrowthCO;
import org.icedamericanomall.dto.MemberLevelCO;
import org.icedamericanomall.dto.TaskCompleteCO;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.model.order.OrderItem;
import org.icedamericanomall.dto.CreateLogisticsCO;
import org.icedamericanomall.dto.StockOpCO;
import org.icedamericanomall.trade.infrastructure.messaging.DomainEventProducer;
import org.icedamericanomall.trade.app.TradeEventNotifier;
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
 * Scenario: 确认收货 → 发放积分（会员倍率，I45）+ 成长值入账（I46）
 * Scenario: 商家发货 → 创建物流记录
 * Scenario: 超时未支付 → 逐单取消并回滚
 * </pre>
 */
@Slf4j
@Component
public class OrderLifecycleManager {

    private static final int POINTS_RATE_DIVISOR = 100;
    private static final int POINTS_TYPE_ORDER_REWARD = 2;
    /** 积分倍率口径（ADR-044）：pointsRate 为百分比，100 = 1 倍。 */
    private static final int POINTS_RATE_PERCENT_DIVISOR = 100;
    /** I45 fail-open 兜底倍率（降级默认 NORMAL = 1 倍，与 MemberClientFallback 口径一致）。 */
    private static final int DEFAULT_POINTS_RATE = 100;
    private static final String POINTS_SOURCE_ORDER_REWARD = "下单奖励";
    /** F9 每日任务编码（ADR-047 task_def.code seed 常量，对应「每日完成 1 笔订单确认」）。 */
    private static final String TASK_CODE_DAILY_ORDER = "DAILY_ORDER";

    private final OrderRepository orderRepository;
    private final SkuClient skuClient;
    private final CouponClient couponClient;
    private final LogisticsClient logisticsClient;
    private final PointsClient pointsClient;
    private final MemberClient memberClient;
    private final TaskClient taskClient;
    private final DomainEventProducer eventProducer;
    private final TradeEventNotifier tradeEventNotifier;

    public OrderLifecycleManager(OrderRepository orderRepository, SkuClient skuClient,
                                  CouponClient couponClient, LogisticsClient logisticsClient,
                                  PointsClient pointsClient, MemberClient memberClient,
                                  TaskClient taskClient,
                                  DomainEventProducer eventProducer,
                                  TradeEventNotifier tradeEventNotifier) {
        this.orderRepository = orderRepository;
        this.skuClient = skuClient;
        this.couponClient = couponClient;
        this.logisticsClient = logisticsClient;
        this.pointsClient = pointsClient;
        this.memberClient = memberClient;
        this.taskClient = taskClient;
        this.eventProducer = eventProducer;
        this.tradeEventNotifier = tradeEventNotifier;
    }

    public void cancelOrder(String orderNo, Long userId) {
        orderRepository.cancelByUser(orderNo, userId);
        Order order = orderRepository.findByOrderNo(orderNo);
        if (order != null) {
            restoreStock(order.getId());
            rollbackCoupon(order);
        }
    }

    /**
     * 确认收货（订单转 COMPLETED）—— 本地状态迁移后于事务外做三件非本地动作：
     * <ol>
     *   <li>发放订单奖励积分（I45 取会员积分倍率，fail-open：查询失败降级 1 倍，不阻断）；</li>
     *   <li>会员成长值入账（I46 → user-service，fail-open：失败记日志不阻断确认收货，
     *       幂等锚点 = orderNo，ADR-044）；</li>
     *   <li>每日任务旁路触发（F9 DAILY_ORDER → marketing-service，ADR-047，fail-open：
     *       降级/异常均不阻断确认收货）。</li>
     * </ol>
     */
    public void confirmReceipt(String orderNo, Long userId) {
        orderRepository.confirmReceiptByUser(orderNo, userId);
        Order order = orderRepository.findByOrderNo(orderNo);
        if (order == null || order.getPayAmount() == null) return;
        awardOrderRewardPoints(order);
        recordMemberGrowth(order);
        completeDailyOrderTask(order);
    }

    /** 订单奖励积分发放：points = payAmount / 100 × pointsRate / 100（整数取整，ADR-044 倍率口径）。 */
    private void awardOrderRewardPoints(Order order) {
        MemberLevelCO level = memberClient.getLevel(order.getUserId()); // I45 fail-open（降级 1 倍）
        int pointsRate = resolvePointsRate(level);
        int points = calculateRewardPoints(order.getPayAmount(), pointsRate);
        if (points <= 0) return;
        try {
            pointsClient.addPoints(order.getUserId(), points, POINTS_TYPE_ORDER_REWARD,
                    POINTS_SOURCE_ORDER_REWARD);
        } catch (Exception e) {
            log.error("下单奖励积分发放失败: orderNo={}", order.getOrderNo(), e);
        }
    }

    /** 积分倍率解析：pointsRate 缺失/非法 → 默认 1 倍（fail-open 口径，与 fallback 一致）。 */
    private int resolvePointsRate(MemberLevelCO level) {
        if (level == null || level.getPointsRate() == null || level.getPointsRate() <= 0) {
            return DEFAULT_POINTS_RATE;
        }
        return level.getPointsRate();
    }

    /**
     * 奖励积分计算（整数取整纪律沿用既有 {@code POINTS_RATE_DIVISOR}：先整除后乘，
     * 从左到右逐级向下取整）—— {@code (payAmount / 100) × pointsRate / 100}。
     * long 中间量防 int 溢出（倍率上限 150，payAmount/100 已收敛，正常远不越界）。
     */
    static int calculateRewardPoints(int payAmount, int pointsRate) {
        long base = payAmount / POINTS_RATE_DIVISOR;
        return (int) (base * pointsRate / POINTS_RATE_PERCENT_DIVISOR);
    }

    /** I46 成长值入账（fail-open，返回值忽略；payAmount ≤ 0 无成长值可记，跳过）。 */
    private void recordMemberGrowth(Order order) {
        if (order.getPayAmount() == null || order.getPayAmount() <= 0) return;
        try {
            MemberGrowthCO growth = new MemberGrowthCO();
            growth.setUserId(order.getUserId());
            growth.setOrderNo(order.getOrderNo());
            growth.setPayAmount(order.getPayAmount());
            memberClient.recordGrowth(growth);
        } catch (Exception e) {
            log.warn("会员成长值入账失败（不阻断确认收货，Phase 1 不补账）: orderNo={}",
                    order.getOrderNo(), e);
        }
    }

    /**
     * F9 每日任务旁路触发（ADR-047，DAILY_ORDER）—— 与 member growth / 积分发放同一编排点，
     * 事务外 fail-open：TaskClient 降级返回 {@code completed=false}（返回值忽略），
     * 异常仅记日志，绝不影响确认收货主流程。
     */
    private void completeDailyOrderTask(Order order) {
        try {
            taskClient.complete(new TaskCompleteCO(order.getUserId(), TASK_CODE_DAILY_ORDER));
        } catch (Exception e) {
            log.warn("每日任务触发失败（不阻断确认收货）: orderNo={}, userId={}",
                    order.getOrderNo(), order.getUserId(), e);
        }
    }

    /**
     * 商家发货（V5.6 拆单发货，F3）—— 本地事务（明细打包裹号 + 订单状态迁移）在 service 层完成，
     * 本方法在其<b>返回后</b>于事务外做三件非本地库动作：创建物流记录（Feign，fail-open 记日志）、
     * 买家发货触达（站内信，ADR-042，fail-open）、发布 order.shipped 事件（MQ，非关键路径）。
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
        // 买家触达（ADR-042）：发货站内信（事务外 fail-open，失败不影响主流程）
        tradeEventNotifier.notifyOrderShipped(order, result.packageNo());
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
