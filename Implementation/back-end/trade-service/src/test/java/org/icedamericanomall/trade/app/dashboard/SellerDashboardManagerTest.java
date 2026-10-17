package org.icedamericanomall.trade.app.dashboard;

import org.icedamericanomall.trade.adapter.response.SellerDashboardVO;
import org.icedamericanomall.trade.adapter.response.SpuStatVO;
import org.icedamericanomall.trade.domain.enums.OrderStatusEnum;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.model.order.OrderItem;
import org.icedamericanomall.trade.domain.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * SellerDashboardManager 单元测试（批次 D：{@code Result<Map>} → 类型化 VO）。
 *
 * <p><b>本测试的职责是「证明没改算法」</b>：改造前返回值是 Map（键为 camelCase 字面量），
 * 现在是 VO —— 只有「同样入参 → 同样数值」才说明这是纯类型重构。因此断言逐一比对
 * 字段值与**分单位金额**，而非只断言类型。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SellerDashboardManager — 类型化 VO 后数值口径不变")
class SellerDashboardManagerTest {

    private static final Long SELLER_ID = 9L;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private SellerDashboardManager sellerDashboardManager;

    @Test
    @DisplayName("dashboard — 今日订单数 / 待发货数 / 本月营收（分）三字段逐一透出")
    void shouldReturnSameNumbers_whenDashboardConverterToVO() {
        when(orderRepository.countBySellerAndCreateTimeAfter(eq(SELLER_ID), any(LocalDateTime.class)))
                .thenReturn(3L);
        when(orderRepository.countBySellerAndStatus(eq(SELLER_ID),
                eq(OrderStatusEnum.PENDING_SHIPMENT.getCode()))).thenReturn(2L);

        Order completed = new Order();
        completed.setTotalAmount(12800);
        when(orderRepository.findBySellerAndStatusAndCreateTimeAfter(eq(SELLER_ID),
                eq(OrderStatusEnum.COMPLETED.getCode()), any(LocalDateTime.class)))
                .thenReturn(List.of(completed));

        SellerDashboardVO vo = sellerDashboardManager.dashboard(SELLER_ID);

        assertEquals(3L, vo.getTodayOrderCount());
        assertEquals(2L, vo.getPendingShipCount());
        // 分：12800 = 128.00 元，不做任何换算
        assertEquals(12800, vo.getMonthlyRevenue());
    }

    @Test
    @DisplayName("dashboard — 无已完成订单 → 营收 0（不是 null）")
    void shouldReturnZeroRevenue_whenNoCompletedOrders() {
        when(orderRepository.countBySellerAndCreateTimeAfter(eq(SELLER_ID), any(LocalDateTime.class)))
                .thenReturn(0L);
        when(orderRepository.countBySellerAndStatus(eq(SELLER_ID),
                eq(OrderStatusEnum.PENDING_SHIPMENT.getCode()))).thenReturn(0L);
        when(orderRepository.findBySellerAndStatusAndCreateTimeAfter(eq(SELLER_ID),
                eq(OrderStatusEnum.COMPLETED.getCode()), any(LocalDateTime.class)))
                .thenReturn(List.of());

        SellerDashboardVO vo = sellerDashboardManager.dashboard(SELLER_ID);

        assertEquals(0, vo.getMonthlyRevenue());
    }

    @Test
    @DisplayName("spuStats — 按 spu_name 快照聚合数量与金额，按金额倒序")
    void shouldAggregateBySnapshotName_whenSpuStats() {
        Order order = new Order();
        order.setId(100L);
        when(orderRepository.findBySellerAndCreateTimeAfter(eq(SELLER_ID), any(LocalDateTime.class)))
                .thenReturn(List.of(order));
        when(orderRepository.findItemsByOrderIds(eq(List.of(100L))))
                .thenReturn(List.of(item("冰美式", 2, 2000), item("冰美式", 3, 3000),
                        item("拿铁", 1, 8000)));

        List<SpuStatVO> rows = sellerDashboardManager.spuStats(SELLER_ID, 30);

        assertEquals(2, rows.size());
        // 金额倒序：拿铁 8000 > 冰美式 5000
        assertEquals("拿铁", rows.get(0).getSpuName());
        assertEquals(1, rows.get(0).getQuantity());
        assertEquals(8000, rows.get(0).getRevenue());
        assertEquals("冰美式", rows.get(1).getSpuName());
        assertEquals(5, rows.get(1).getQuantity());
        assertEquals(5000, rows.get(1).getRevenue());
    }

    @Test
    @DisplayName("spuStats — 无订单 → 空列表（不是 null，也不是一个空 Map）")
    void shouldReturnEmptyList_whenNoOrdersInWindow() {
        when(orderRepository.findBySellerAndCreateTimeAfter(eq(SELLER_ID), any(LocalDateTime.class)))
                .thenReturn(List.of());

        assertTrue(sellerDashboardManager.spuStats(SELLER_ID, 7).isEmpty());
    }

    private static OrderItem item(String spuName, int quantity, int subTotal) {
        OrderItem item = new OrderItem();
        item.setSpuName(spuName);
        item.setQuantity(quantity);
        item.setSubTotal(subTotal);
        item.setCreateTime(LocalDate.now().atStartOfDay());
        return item;
    }
}
