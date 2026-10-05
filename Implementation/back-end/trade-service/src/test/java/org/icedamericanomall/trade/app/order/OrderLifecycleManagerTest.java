package org.icedamericanomall.trade.app.order;

import org.icedamericanomall.client.CouponClient;
import org.icedamericanomall.client.LogisticsClient;
import org.icedamericanomall.client.MemberClient;
import org.icedamericanomall.client.NotificationClient;
import org.icedamericanomall.client.PointsClient;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.client.TaskClient;
import org.icedamericanomall.dto.InAppMessageSendReqCO;
import org.icedamericanomall.dto.MemberGrowthCO;
import org.icedamericanomall.dto.MemberLevelCO;
import org.icedamericanomall.dto.TaskCompleteCO;
import org.icedamericanomall.dto.TaskCompleteResultCO;
import org.icedamericanomall.trade.app.TradeEventNotifier;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.model.order.OrderItem;
import org.icedamericanomall.trade.domain.enums.OrderStatusEnum;
import org.icedamericanomall.trade.domain.model.order.ShipPackageResult;
import org.icedamericanomall.trade.domain.repository.OrderRepository;
import org.icedamericanomall.trade.infrastructure.messaging.DomainEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OrderLifecycleManager 单元测试 —— 超时取消的幂等分支：
 * 条件更新未命中（受影响行数=0）时不得回滚库存/优惠券，避免并发双重复原。
 * 发货买家触达（ADR-042）经真实 {@link TradeEventNotifier} 打通到 NotificationClient。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderLifecycleManager 取消编排单元测试")
class OrderLifecycleManagerTest {

    @Mock private OrderRepository orderRepository;
    @Mock private SkuClient skuClient;
    @Mock private CouponClient couponClient;
    @Mock private LogisticsClient logisticsClient;
    @Mock private PointsClient pointsClient;
    @Mock private MemberClient memberClient;
    @Mock private TaskClient taskClient;
    @Mock private DomainEventProducer eventProducer;
    @Mock private NotificationClient notificationClient;

    private OrderLifecycleManager manager;

    @BeforeEach
    void setUp() {
        manager = new OrderLifecycleManager(orderRepository, skuClient, couponClient,
                logisticsClient, pointsClient, memberClient, taskClient, eventProducer,
                new TradeEventNotifier(notificationClient));
    }

    private Order order() {
        Order o = new Order();
        o.setId(1L);
        o.setOrderNo("ORD-1");
        o.setUserId(100L);
        o.setSellerId(9L);
        o.setStatus(OrderStatusEnum.PENDING_PAYMENT.getCode());
        o.setPayAmount(10000);
        o.setDiscountAmount(2000);
        return o;
    }

    private OrderItem item() {
        OrderItem i = new OrderItem();
        i.setOrderId(1L);
        i.setSkuId(1000L);
        i.setQuantity(2);
        return i;
    }

    @Test
    @DisplayName("cancelOrderSafely — 条件更新命中：回滚库存 + 优惠券，返回 true")
    void shouldCompensate_whenConditionalCancelHits() {
        when(orderRepository.findByOrderNo("ORD-1")).thenReturn(order());
        when(orderRepository.closeTimeoutOrder(1L)).thenReturn(true);
        when(orderRepository.findItemsByOrderId(1L)).thenReturn(List.of(item()));

        assertTrue(manager.cancelOrderSafely("ORD-1", 100L));

        verify(skuClient).restoreStock(anyList());
        verify(couponClient).rollbackByOrderNo("ORD-1");
    }

    @Test
    @DisplayName("cancelOrderSafely — 条件更新未命中（0 行）：不回滚库存/券，返回 false")
    void shouldNotCompensate_whenConditionalCancelMisses() {
        when(orderRepository.findByOrderNo("ORD-1")).thenReturn(order());
        when(orderRepository.closeTimeoutOrder(1L)).thenReturn(false);

        assertFalse(manager.cancelOrderSafely("ORD-1", 100L));

        verify(skuClient, never()).restoreStock(anyList());
        verify(couponClient, never()).rollbackByOrderNo(anyString());
    }

    @Test
    @DisplayName("cancelOrderSafely — 订单不存在：返回 false 且无任何补偿")
    void shouldNotCompensate_whenOrderMissing() {
        when(orderRepository.findByOrderNo("ORD-NONE")).thenReturn(null);

        assertFalse(manager.cancelOrderSafely("ORD-NONE", 100L));

        verify(orderRepository, never()).closeTimeoutOrder(anyLong());
        verify(skuClient, never()).restoreStock(anyList());
    }

    // ==================== 确认收货：积分倍率（I45）+ 成长值入账（I46，ADR-044） ====================

    @Test
    @DisplayName("confirmReceipt — GOLD 会员 1.2 倍：points = 10000/100 × 120 / 100 = 120")
    void shouldApplyGoldPointsMultiplier_whenConfirmReceipt() {
        when(orderRepository.findByOrderNo("ORD-1")).thenReturn(order());
        when(memberClient.getLevel(100L)).thenReturn(new MemberLevelCO("GOLD", 9800, 120));

        manager.confirmReceipt("ORD-1", 100L);

        verify(pointsClient).addPoints(eq(100L), eq(120), eq(2), eq("下单奖励"));
    }

    @Test
    @DisplayName("confirmReceipt — NORMAL 会员 1 倍：points = 10000/100 × 100 / 100 = 100")
    void shouldAwardBasePoints_whenNormalLevel() {
        when(orderRepository.findByOrderNo("ORD-1")).thenReturn(order());
        when(memberClient.getLevel(100L)).thenReturn(new MemberLevelCO("NORMAL", 10000, 100));

        manager.confirmReceipt("ORD-1", 100L);

        verify(pointsClient).addPoints(eq(100L), eq(100), eq(2), eq("下单奖励"));
    }

    @Test
    @DisplayName("confirmReceipt — I45 降级（fallback 返回 null）：按默认 1 倍发放，不阻断")
    void shouldUseDefaultRate_whenMemberLevelMissing() {
        when(orderRepository.findByOrderNo("ORD-1")).thenReturn(order());
        when(memberClient.getLevel(100L)).thenReturn(null);

        manager.confirmReceipt("ORD-1", 100L);

        verify(pointsClient).addPoints(eq(100L), eq(100), eq(2), eq("下单奖励"));
    }

    @Test
    @DisplayName("confirmReceipt — I46 recordGrowth 收到 userId/orderNo/payAmount（幂等锚点）")
    void shouldRecordGrowthWithOrderFacts_whenConfirmReceipt() {
        when(orderRepository.findByOrderNo("ORD-1")).thenReturn(order());
        when(memberClient.getLevel(100L)).thenReturn(new MemberLevelCO("NORMAL", 10000, 100));

        manager.confirmReceipt("ORD-1", 100L);

        ArgumentCaptor<MemberGrowthCO> captor = ArgumentCaptor.forClass(MemberGrowthCO.class);
        verify(memberClient).recordGrowth(captor.capture());
        MemberGrowthCO growth = captor.getValue();
        assertEquals(100L, growth.getUserId());
        assertEquals("ORD-1", growth.getOrderNo());
        assertEquals(10000, growth.getPayAmount());
    }

    @Test
    @DisplayName("confirmReceipt — I46 入账失败 fail-open：不抛异常，积分照常发放（确认收货不被阻断）")
    void shouldNotBlockConfirmReceipt_whenGrowthRecordingFails() {
        when(orderRepository.findByOrderNo("ORD-1")).thenReturn(order());
        when(memberClient.getLevel(100L)).thenReturn(new MemberLevelCO("NORMAL", 10000, 100));
        doThrow(new RuntimeException("user-service down")).when(memberClient).recordGrowth(any());

        assertDoesNotThrow(() -> manager.confirmReceipt("ORD-1", 100L));

        verify(pointsClient).addPoints(eq(100L), eq(100), eq(2), eq("下单奖励"));
    }

    @Test
    @DisplayName("confirmReceipt — F9 每日任务触发：DAILY_ORDER 携带 userId（ADR-047）")
    void shouldTriggerDailyOrderTask_whenConfirmReceipt() {
        when(orderRepository.findByOrderNo("ORD-1")).thenReturn(order());
        when(memberClient.getLevel(100L)).thenReturn(new MemberLevelCO("NORMAL", 10000, 100));

        manager.confirmReceipt("ORD-1", 100L);

        ArgumentCaptor<TaskCompleteCO> captor = ArgumentCaptor.forClass(TaskCompleteCO.class);
        verify(taskClient).complete(captor.capture());
        assertEquals(100L, captor.getValue().getUserId());
        assertEquals("DAILY_ORDER", captor.getValue().getTaskCode());
    }

    @Test
    @DisplayName("confirmReceipt — 任务触发异常 fail-open：确认收货主流程不受影响（积分/成长值照常）")
    void shouldNotBlockConfirmReceipt_whenTaskTriggerFails() {
        when(orderRepository.findByOrderNo("ORD-1")).thenReturn(order());
        when(memberClient.getLevel(100L)).thenReturn(new MemberLevelCO("NORMAL", 10000, 100));
        doThrow(new RuntimeException("marketing-service down")).when(taskClient).complete(any());

        assertDoesNotThrow(() -> manager.confirmReceipt("ORD-1", 100L));

        verify(pointsClient).addPoints(eq(100L), eq(100), eq(2), eq("下单奖励"));
        verify(memberClient).recordGrowth(any());
    }

    @Test
    @DisplayName("confirmReceipt — 任务降级返回 false+0（fallback 口径）：返回值忽略，主流程零感知")
    void shouldIgnoreDegradedTaskResult_whenConfirmReceipt() {
        when(orderRepository.findByOrderNo("ORD-1")).thenReturn(order());
        when(memberClient.getLevel(100L)).thenReturn(new MemberLevelCO("NORMAL", 10000, 100));
        when(taskClient.complete(any(TaskCompleteCO.class)))
                .thenReturn(new TaskCompleteResultCO(false, 0));

        assertDoesNotThrow(() -> manager.confirmReceipt("ORD-1", 100L));

        verify(taskClient).complete(any(TaskCompleteCO.class));
        verify(pointsClient).addPoints(eq(100L), eq(100), eq(2), eq("下单奖励"));
    }

    // ==================== 拆单发货（F3）编排 ====================

    @Test
    @DisplayName("shipOrder — 包裹号由仓储算出并原样传入物流创建请求（orderId + packageNo）")
    void shouldPassPackageNoToLogistics_whenShippingPackage() {
        Order shipped = order();
        shipped.setStatus(OrderStatusEnum.PENDING_RECEIPT.getCode());
        shipped.setReceiverName("张三");
        shipped.setReceiverPhone("13800138000");
        when(orderRepository.shipPackage("ORD-1", 9L, List.of(1000L)))
                .thenReturn(new ShipPackageResult(1L, 2, List.of(1000L), true));
        when(orderRepository.findByOrderNo("ORD-1")).thenReturn(shipped);

        manager.shipOrder("ORD-1", 9L, "SF002", "顺丰", List.of(1000L));

        ArgumentCaptor<org.icedamericanomall.dto.CreateLogisticsCO> captor =
                ArgumentCaptor.forClass(org.icedamericanomall.dto.CreateLogisticsCO.class);
        verify(logisticsClient).createLogistics(captor.capture());
        assertEquals(1L, captor.getValue().getOrderId());
        assertEquals(2, captor.getValue().getPackageNo());
        assertEquals("SF002", captor.getValue().getLogisticsNumber());
        verify(eventProducer).publishOrderShipped(any());
    }

    @Test
    @DisplayName("shipOrder — 物流创建失败：fail-open 不抛出（发货事实已落库，不反悔）")
    void shouldNotFail_whenLogisticsCreationFails() {
        Order shipped = order();
        when(orderRepository.shipPackage("ORD-1", 9L, null))
                .thenReturn(new ShipPackageResult(1L, 1, List.of(1000L), true));
        when(orderRepository.findByOrderNo("ORD-1")).thenReturn(shipped);
        doThrow(new RuntimeException("logistics service down"))
                .when(logisticsClient).createLogistics(any());

        assertDoesNotThrow(() -> manager.shipOrder("ORD-1", 9L, "SF001", "顺丰", null));

        verify(eventProducer).publishOrderShipped(any());
    }

    @Test
    @DisplayName("发货触达 — 物流创建后买家收到 ORDER_SHIPPED 站内信（订单号+包裹号+订单链接）")
    void shouldNotifyBuyer_whenPackageShipped() {
        Order shipped = order();
        shipped.setStatus(OrderStatusEnum.PENDING_RECEIPT.getCode());
        shipped.setReceiverName("张三");
        shipped.setReceiverPhone("13800138000");
        when(orderRepository.shipPackage("ORD-1", 9L, List.of(1000L)))
                .thenReturn(new ShipPackageResult(1L, 2, List.of(1000L), true));
        when(orderRepository.findByOrderNo("ORD-1")).thenReturn(shipped);

        manager.shipOrder("ORD-1", 9L, "SF002", "顺丰", List.of(1000L));

        ArgumentCaptor<InAppMessageSendReqCO> captor = ArgumentCaptor.forClass(InAppMessageSendReqCO.class);
        verify(notificationClient).sendInAppMessage(captor.capture());
        InAppMessageSendReqCO message = captor.getValue();
        assertEquals("ORDER_SHIPPED", message.getType());
        assertEquals(100L, message.getRecipientId());
        assertTrue(message.getContent().contains("ORD-1"), "内容须含订单号: " + message.getContent());
        assertTrue(message.getContent().contains("第 2 个包裹"), "内容须含包裹号: " + message.getContent());
        assertEquals("/orders/ORD-1", message.getLinkUrl());
        verify(eventProducer).publishOrderShipped(any());
    }

    @Test
    @DisplayName("触达失败不侵染发货 — NotificationClient 抛异常：物流创建与事件发布照常完成")
    void shouldStillCompleteShipping_whenBuyerNotificationFails() {
        Order shipped = order();
        shipped.setStatus(OrderStatusEnum.PENDING_RECEIPT.getCode());
        when(orderRepository.shipPackage("ORD-1", 9L, null))
                .thenReturn(new ShipPackageResult(1L, 1, List.of(1000L), true));
        when(orderRepository.findByOrderNo("ORD-1")).thenReturn(shipped);
        doThrow(new RuntimeException("notification-service down"))
                .when(notificationClient).sendInAppMessage(any(InAppMessageSendReqCO.class));

        assertDoesNotThrow(() -> manager.shipOrder("ORD-1", 9L, "SF001", "顺丰", null));

        verify(logisticsClient).createLogistics(any());
        verify(eventProducer).publishOrderShipped(any());
    }
}
