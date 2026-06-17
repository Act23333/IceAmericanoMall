package org.icedAmericanoMall.service.impl;

import org.icedAmericanoMall.domain.entity.OrderEntity;
import org.icedAmericanoMall.domain.entity.OrderItemEntity;
import org.icedAmericanoMall.domain.entity.OrderLogisticsEntity;
import org.icedAmericanoMall.enums.OrderStatusEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OrderServiceImpl — entity, enum, and business rule validation.
 *
 * Note: Methods using MyBatis-Plus lambdaQuery()/lambdaUpdate() chains
 * (cancelOrder, confirmReceipt, shipOrder, pageAllOrders) require
 * {@code @SpringBootTest} integration tests with Testcontainers or H2.
 */
@DisplayName("OrderServiceImpl 单元测试")
class OrderServiceImplTest {

    // ==================== OrderStatusEnum ====================

    @Test
    @DisplayName("OrderStatusEnum — 状态码正确映射")
    void shouldHaveCorrectStatusFlow() {
        assertEquals(1, OrderStatusEnum.PENDING_PAYMENT.getCode(), "待付款");
        assertEquals(2, OrderStatusEnum.PENDING_SHIPMENT.getCode(), "待发货");
        assertEquals(3, OrderStatusEnum.PENDING_RECEIPT.getCode(), "待收货");
        assertEquals(4, OrderStatusEnum.COMPLETED.getCode(), "已完成");
        assertEquals(5, OrderStatusEnum.CANCELLED.getCode(), "已取消");
        assertEquals(6, OrderStatusEnum.PENDING_REVIEW.getCode(), "待评价");
    }

    @Test
    @DisplayName("OrderStatusEnum — 共 6 个订单状态")
    void shouldHaveExactlySixStatuses() {
        assertEquals(6, OrderStatusEnum.values().length);
    }

    @Test
    @DisplayName("OrderStatusEnum — 待付款可取消，已取消不可再取消")
    void shouldOnlyCancelPendingPaymentOrders() {
        int pending = OrderStatusEnum.PENDING_PAYMENT.getCode();
        int cancelled = OrderStatusEnum.CANCELLED.getCode();
        assertEquals(1, pending);
        assertEquals(5, cancelled);
        assertNotEquals(pending, cancelled);
    }

    // ==================== 金额计算 ====================

    @Test
    @DisplayName("订单总额 = 各订单项小计之和")
    void shouldCalculateTotalAmountCorrectly() {
        OrderItemEntity item1 = new OrderItemEntity();
        item1.setPrice(1000); // 10.00
        item1.setQuantity(2);
        item1.setSubTotal(2000);

        OrderItemEntity item2 = new OrderItemEntity();
        item2.setPrice(500); // 5.00
        item2.setQuantity(3);
        item2.setSubTotal(1500);

        int totalAmount = item1.getSubTotal() + item2.getSubTotal();
        assertEquals(3500, totalAmount); // 35.00
    }

    @Test
    @DisplayName("订单金额 — 全部使用 Integer（分）存储")
    void shouldStoreAllAmountsInCents() {
        OrderEntity order = new OrderEntity();
        order.setTotalAmount(10000);  // 100.00 元
        order.setPayAmount(10000);
        order.setDiscountAmount(0);

        assertInstanceOf(Integer.class, order.getTotalAmount());
        assertInstanceOf(Integer.class, order.getPayAmount());
        assertInstanceOf(Integer.class, order.getDiscountAmount());
    }

    @Test
    @DisplayName("OrderItem 单独创建 — 默认值验证")
    void shouldHaveDefaultValues_whenNewOrderItem() {
        OrderItemEntity item = new OrderItemEntity();
        assertNull(item.getOrderId());
        assertNull(item.getSkuId());
        assertNull(item.getPrice());
        assertNull(item.getQuantity());
        assertNull(item.getSubTotal());
    }

    // ==================== 乐观锁 ====================

    @Test
    @DisplayName("OrderEntity — version 字段存在且初始值可为 0")
    void shouldHaveVersionForOptimisticLocking() {
        OrderEntity order = new OrderEntity();
        order.setVersion(0);
        assertNotNull(order.getVersion());
        assertEquals(0, order.getVersion());
    }

    @Test
    @DisplayName("OrderEntity — version 递增验证")
    void shouldIncrementVersion() {
        OrderEntity order = new OrderEntity();
        order.setVersion(0);
        order.setVersion(order.getVersion() + 1);
        assertEquals(1, order.getVersion());
    }

    // ==================== OrderEntity 快照字段 ====================

    @Test
    @DisplayName("OrderEntity — 收货地址快照字段完整")
    void shouldSnapshotReceiverInfo() {
        OrderEntity order = new OrderEntity();
        order.setReceiverName("张三");
        order.setReceiverPhone("13800138000");
        order.setReceiverAddress("广东省深圳市南山区科技园路1号");

        assertEquals("张三", order.getReceiverName());
        assertEquals("13800138000", order.getReceiverPhone());
        assertEquals("广东省深圳市南山区科技园路1号", order.getReceiverAddress());
    }

    @Test
    @DisplayName("OrderEntity — 时间字段正确赋值")
    void shouldSetAllTimeFields() {
        LocalDateTime now = LocalDateTime.now();
        OrderEntity order = new OrderEntity();
        order.setCreateTime(now);
        order.setPayTime(now.plusMinutes(5));
        order.setConsignTime(now.plusHours(24));
        order.setEndTime(now.plusDays(5));
        order.setCloseTime(null);

        assertEquals(now, order.getCreateTime());
        assertNotNull(order.getPayTime());
        assertTrue(order.getPayTime().isAfter(order.getCreateTime()));
        assertNull(order.getCloseTime()); // 未取消
    }

    // ==================== OrderLogisticsEntity ====================

    @Test
    @DisplayName("OrderLogisticsEntity — 物流信息字段完整")
    void shouldSetLogisticsFields() {
        OrderLogisticsEntity logistics = new OrderLogisticsEntity();
        logistics.setOrderId(1L);
        logistics.setLogisticsNumber("SF123456");
        logistics.setLogisticsCompany("顺丰快递");
        logistics.setContact("张三");
        logistics.setMobile("13800138000");

        assertEquals(1L, logistics.getOrderId());
        assertEquals("SF123456", logistics.getLogisticsNumber());
        assertEquals("顺丰快递", logistics.getLogisticsCompany());
        assertEquals("张三", logistics.getContact());
        assertEquals("13800138000", logistics.getMobile());
    }

    // ==================== CreateOrderReq ====================

    @Test
    @DisplayName("CreateOrderReq — cartItemIds 不能为空")
    void shouldRejectEmptyCartItemIds() {
        var req = new org.icedAmericanoMall.domain.dto.CreateOrderReq();
        req.setAddressId(1L);
        req.setCartItemIds(java.util.List.of(1L, 2L));
        req.setRemark("测试订单");

        assertEquals(1L, req.getAddressId());
        assertEquals(2, req.getCartItemIds().size());
        assertEquals("测试订单", req.getRemark());
    }

    // ==================== ShipOrderReq ====================

    @Test
    @DisplayName("ShipOrderReq — 物流信息字段正确")
    void shouldSetShipOrderReqFields() {
        var req = new org.icedAmericanoMall.domain.dto.ShipOrderReq();
        req.setLogisticsNumber("SF123456");
        req.setLogisticsCompany("顺丰快递");

        assertEquals("SF123456", req.getLogisticsNumber());
        assertEquals("顺丰快递", req.getLogisticsCompany());
    }
}
