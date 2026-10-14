package org.icedamericanomall.trade.infrastructure.persistence.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.model.order.OrderItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

/**
 * OrderRepositoryImpl 单元测试（Mock Mapper）—— saveWithItems 的事务内写入契约：
 * 订单落库、订单项关联 orderId、createTime 自动设置。
 * lambdaQuery/lambdaUpdate 链相关的条件更新由 {@link OrderRepositoryImplH2Test} 以 H2 覆盖。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderRepositoryImpl 写入契约单元测试")
class OrderRepositoryImplTest {

    @Mock
    private OrderMapper mockOrderMapper;
    @Mock
    private OrderItemMapper mockOrderItemMapper;

    private OrderRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new OrderRepositoryImpl(mockOrderMapper, mockOrderItemMapper);
    }

    @Test
    @DisplayName("saveWithItems — 事务中创建订单和订单项，订单项关联 orderId")
    void shouldCreateOrderAndItems_whenValidInput() {
        Order order = new Order();
        order.setOrderNo("aaaaaaaa000070008000000000000001");
        order.setUserId(100L);
        order.setTotalAmount(129900);

        OrderItem item1 = new OrderItem();
        item1.setSkuId(1L);
        item1.setPrice(89900);
        item1.setQuantity(1);

        OrderItem item2 = new OrderItem();
        item2.setSkuId(2L);
        item2.setPrice(40000);
        item2.setQuantity(1);

        List<OrderItem> items = Arrays.asList(item1, item2);

        repository.saveWithItems(order, items);

        // 订单与订单项均落库（id 由 MyBatis-Plus 回填，此处验证关联逻辑）
        assertNotNull(order.getCreateTime());
        assertEquals(order.getId(), item1.getOrderId(), "订单项应关联到订单ID");
        assertEquals(order.getId(), item2.getOrderId(), "订单项应关联到订单ID");
    }

    @Test
    @DisplayName("saveWithItems — 空订单项列表也能正常保存订单")
    void shouldCreateOrder_whenEmptyItems() {
        Order order = new Order();
        order.setOrderNo("aaaaaaaa000070008000000000000002");
        order.setUserId(100L);

        repository.saveWithItems(order, List.of());

        verify(mockOrderMapper).insert(org.mockito.ArgumentMatchers.any(OrderPO.class));
        assertNotNull(order.getCreateTime());
    }

    @Test
    @DisplayName("saveWithItems — createTime 自动设置为当前时间")
    void shouldAutoSetCreateTime_whenCreatingOrder() {
        Order order = new Order();
        order.setOrderNo("aaaaaaaa000070008000000000000003");
        order.setUserId(100L);

        repository.saveWithItems(order, List.of());

        assertNotNull(order.getCreateTime(), "创建时间应被自动设置");
    }
}
