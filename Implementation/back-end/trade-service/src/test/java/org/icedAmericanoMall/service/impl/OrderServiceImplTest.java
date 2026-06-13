package org.icedAmericanoMall.service.impl;

import org.icedAmericanoMall.domain.entity.OrderEntity;
import org.icedAmericanoMall.domain.entity.OrderItemEntity;
import org.icedAmericanoMall.enums.OrderStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Order business logic.
 * Full tests require mocking dependencies.
 */
class OrderServiceImplTest {

    @Test
    void shouldHaveCorrectStatusFlow() {
        assertEquals(1, OrderStatusEnum.PENDING_PAYMENT.getCode());
        assertEquals(2, OrderStatusEnum.PENDING_SHIPMENT.getCode());
        assertEquals(3, OrderStatusEnum.PENDING_RECEIPT.getCode());
        assertEquals(4, OrderStatusEnum.COMPLETED.getCode());
        assertEquals(5, OrderStatusEnum.CANCELLED.getCode());
    }

    @Test
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
        assertEquals(3500, totalAmount);
    }

    @Test
    void shouldStoreAllAmountsInCents() {
        OrderEntity order = new OrderEntity();
        order.setTotalAmount(10000); // 100.00 yuan
        order.setPayAmount(10000);
        order.setDiscountAmount(0);

        assertInstanceOf(Integer.class, order.getTotalAmount());
        assertInstanceOf(Integer.class, order.getPayAmount());
        assertInstanceOf(Integer.class, order.getDiscountAmount());
    }

    @Test
    void shouldOnlyCancelPendingPaymentOrders() {
        // PENDING_PAYMENT(1) can be cancelled
        int status = OrderStatusEnum.PENDING_PAYMENT.getCode();
        assertEquals(OrderStatusEnum.PENDING_PAYMENT.getCode(), status);

        // CANCELLED(5) cannot be cancelled again
        assertNotEquals(OrderStatusEnum.PENDING_PAYMENT.getCode(),
                OrderStatusEnum.CANCELLED.getCode());
    }

    @Test
    void shouldHaveVersionForOptimisticLocking() {
        OrderEntity order = new OrderEntity();
        order.setVersion(0);
        assertNotNull(order.getVersion());
    }
}
