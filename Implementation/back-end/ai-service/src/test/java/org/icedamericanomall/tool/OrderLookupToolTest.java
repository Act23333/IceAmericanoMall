package org.icedamericanomall.tool;

import org.icedamericanomall.client.OrderClient;
import org.icedamericanomall.dto.OrderSummaryDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderLookupToolTest {

    @Mock
    private OrderClient orderClient;

    private OrderLookupTool tool;

    @BeforeEach
    void setUp() {
        tool = new OrderLookupTool(orderClient);
    }

    @Test
    @DisplayName("查订单 → 待发货状态")
    void shouldShowPendingShip_whenStatusIs2() {
        OrderSummaryDTO order = new OrderSummaryDTO();
        order.setOrderNo("ORD-2026-001");
        order.setTotalAmount(49900);
        order.setStatus(2); // 待发货
        when(orderClient.getOrder(eq("ORD-2026-001"))).thenReturn(order);

        String result = tool.lookupOrder("ORD-2026-001");
        assertTrue(result.contains("ORD-2026-001"));
        assertTrue(result.contains("待发货"));
        assertTrue(result.contains("499.00"));
    }

    @Test
    @DisplayName("查订单 → 已取消状态")
    void shouldShowCancelled_whenStatusIs5() {
        OrderSummaryDTO order = new OrderSummaryDTO();
        order.setOrderNo("ORD-X");
        order.setTotalAmount(10000);
        order.setStatus(5);
        when(orderClient.getOrder(eq("ORD-X"))).thenReturn(order);

        String result = tool.lookupOrder("ORD-X");
        assertTrue(result.contains("已取消"));
    }
}
