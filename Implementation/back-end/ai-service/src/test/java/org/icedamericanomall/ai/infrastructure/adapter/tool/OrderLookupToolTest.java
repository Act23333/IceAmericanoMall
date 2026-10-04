package org.icedamericanomall.ai.infrastructure.adapter.tool;

import org.icedamericanomall.ai.domain.model.order.OrderBrief;
import org.icedamericanomall.ai.domain.port.OrderPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * OrderLookupTool 单元测试 —— 只依赖领域端口 OrderPort。
 */
@ExtendWith(MockitoExtension.class)
class OrderLookupToolTest {

    @Mock
    private OrderPort orderPort;

    private OrderLookupTool tool;

    @BeforeEach
    void setUp() {
        tool = new OrderLookupTool(orderPort);
    }

    @Test
    @DisplayName("查订单 → 待发货状态")
    void shouldShowPendingShip_whenStatusIs2() {
        when(orderPort.getOrder(eq("ORD-2026-001")))
                .thenReturn(new OrderBrief("ORD-2026-001", 2, 49900));

        String result = tool.lookupOrder("ORD-2026-001");
        assertTrue(result.contains("ORD-2026-001"));
        assertTrue(result.contains("待发货"));
        assertTrue(result.contains("499.00"));
    }

    @Test
    @DisplayName("查订单 → 已取消状态")
    void shouldShowCancelled_whenStatusIs5() {
        when(orderPort.getOrder(eq("ORD-X"))).thenReturn(new OrderBrief("ORD-X", 5, 10000));

        assertTrue(tool.lookupOrder("ORD-X").contains("已取消"));
    }

    @Test
    @DisplayName("查订单 → 订单不存在时提示核对订单号")
    void shouldReturnNotFound_whenOrderAbsent() {
        when(orderPort.getOrder(eq("ORD-NONE"))).thenReturn(null);

        assertTrue(tool.lookupOrder("ORD-NONE").contains("未找到订单"));
    }
}
