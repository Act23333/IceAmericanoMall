package org.icedamericanomall.trade.app.order;

import org.icedamericanomall.client.CouponClient;
import org.icedamericanomall.client.LogisticsClient;
import org.icedamericanomall.client.PointsClient;
import org.icedamericanomall.client.SkuClient;
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
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderLifecycleManager 取消编排单元测试")
class OrderLifecycleManagerTest {

    @Mock private OrderRepository orderRepository;
    @Mock private SkuClient skuClient;
    @Mock private CouponClient couponClient;
    @Mock private LogisticsClient logisticsClient;
    @Mock private PointsClient pointsClient;
    @Mock private DomainEventProducer eventProducer;

    private OrderLifecycleManager manager;

    @BeforeEach
    void setUp() {
        manager = new OrderLifecycleManager(orderRepository, skuClient, couponClient,
                logisticsClient, pointsClient, eventProducer);
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
}
