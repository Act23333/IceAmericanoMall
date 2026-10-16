package org.icedamericanomall.trade.app.order;

import org.icedamericanomall.client.GroupBuyClient;
import org.icedamericanomall.client.OrderRefundClient;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.GroupJoinCO;
import org.icedamericanomall.dto.GroupTeamCO;
import org.icedamericanomall.dto.GroupTeamRefundCO;
import org.icedamericanomall.dto.OrderRefundCO;
import org.icedamericanomall.dto.StockOpCO;
import org.icedamericanomall.trade.domain.enums.OrderStatusEnum;
import org.icedamericanomall.trade.domain.enums.OrderTypeEnum;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.model.order.OrderItem;
import org.icedamericanomall.trade.domain.repository.OrderRepository;
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
 * GroupBuyParticipationService 单元测试（F13, ADR-049）：
 * 支付成功参团钩子（非拼团单过滤 / join 成功 / join 失败 → 取消+恢复库存+I55 退款闭环 /
 * 上下文缺失 → 闭环 / I55 失败不外抛）与 I54 结团逐单退款（单笔失败继续 / 非待发货跳过）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GroupBuyParticipationService 参团/结团退款闭环单元测试")
class GroupBuyParticipationServiceTest {
    /** SKU 业务键（32-hex，ADR-061 §决策 2：线格式与订单项标识一律业务键）。 */
    private static final String SKU_BUSINESS_ID = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";
    private static final String SKU_BUSINESS_ID_2 = "112233445566778899aabbccddeeff00";
    private static final String SPU_BUSINESS_ID = "aabbccddeeff00112233445566778899";

    private static final String ORDER_NO = "GB-ORD-1";

    @Mock private OrderRepository orderRepository;
    @Mock private SkuClient skuClient;
    @Mock private GroupBuyClient groupBuyClient;
    @Mock private OrderRefundClient orderRefundClient;
    @Mock private GroupBuyJoinContextStore joinContextStore;

    private GroupBuyParticipationService service;

    @BeforeEach
    void setUp() {
        service = new GroupBuyParticipationService(
                orderRepository, skuClient, groupBuyClient, orderRefundClient, joinContextStore);
    }

    private Order order(int orderType) {
        Order o = new Order();
        o.setId(1L);
        o.setOrderNo(ORDER_NO);
        o.setUserId(100L);
        o.setStatus(OrderStatusEnum.PENDING_SHIPMENT.getCode());
        o.setOrderType(orderType);
        return o;
    }

    private OrderItem item() {
        OrderItem i = new OrderItem();
        i.setOrderId(1L);
        i.setSkuId(SKU_BUSINESS_ID);
        i.setQuantity(1);
        return i;
    }

    private GroupBuyJoinContextStore.GroupBuyJoinCtx ctx(Long activityId, String teamNo) {
        return new GroupBuyJoinContextStore.GroupBuyJoinCtx(activityId, teamNo);
    }

    /** 打桩「取消成功 + 有明细」的退款闭环前置。 */
    private void stubClosureSuccess() {
        when(orderRepository.cancelPaidBySystem(ORDER_NO)).thenReturn(true);
        when(orderRepository.findItemsByOrderId(1L)).thenReturn(List.of(item()));
    }

    // ==================== 支付成功参团钩子 ====================

    @Test
    @DisplayName("participateAfterPayment — 非拼团单：直接返回，零额外动作")
    void shouldSkip_whenOrderNotGroupBuy() {
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(order(OrderTypeEnum.NORMAL.getCode()));

        service.participateAfterPayment(ORDER_NO);

        verifyNoInteractions(joinContextStore, groupBuyClient, orderRefundClient);
        verify(orderRepository, never()).cancelPaidBySystem(anyString());
    }

    @Test
    @DisplayName("participateAfterPayment — 上下文在：I53 参团携带 userId/orderNo/activityId/teamNo")
    void shouldJoinWithOrderFacts_whenContextPresent() {
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(order(OrderTypeEnum.GROUP_BUY.getCode()));
        when(joinContextStore.take(ORDER_NO)).thenReturn(ctx(1L, "T-1"));
        when(groupBuyClient.join(any(GroupJoinCO.class))).thenReturn(new GroupTeamCO("T-1", 1, 1, 3));

        assertDoesNotThrow(() -> service.participateAfterPayment(ORDER_NO));

        ArgumentCaptor<GroupJoinCO> captor = ArgumentCaptor.forClass(GroupJoinCO.class);
        verify(groupBuyClient).join(captor.capture());
        assertEquals(100L, captor.getValue().getUserId());
        assertEquals(ORDER_NO, captor.getValue().getOrderNo());
        assertEquals(1L, captor.getValue().getActivityId());
        assertEquals("T-1", captor.getValue().getTeamNo());
        verify(orderRepository, never()).cancelPaidBySystem(anyString());
        verifyNoInteractions(orderRefundClient);
    }

    @Test
    @DisplayName("participateAfterPayment — teamNo 为空 = 开团语义原样透传 I53")
    void shouldJoinAsOpenTeam_whenTeamNoNull() {
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(order(OrderTypeEnum.GROUP_BUY.getCode()));
        when(joinContextStore.take(ORDER_NO)).thenReturn(ctx(1L, null));
        when(groupBuyClient.join(any(GroupJoinCO.class))).thenReturn(new GroupTeamCO("NEW-TEAM", 1, 1, 3));

        service.participateAfterPayment(ORDER_NO);

        ArgumentCaptor<GroupJoinCO> captor = ArgumentCaptor.forClass(GroupJoinCO.class);
        verify(groupBuyClient).join(captor.capture());
        assertNull(captor.getValue().getTeamNo(), "teamNo 空 = 开团");
    }

    @Test
    @DisplayName("participateAfterPayment — join 失败（团满/已结/降级）：条件取消 + 恢复库存 + I55 退款（sceneRef=GB:orderNo）")
    void shouldTriggerRefundClosure_whenJoinFails() {
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(order(OrderTypeEnum.GROUP_BUY.getCode()));
        when(joinContextStore.take(ORDER_NO)).thenReturn(ctx(1L, "T-1"));
        when(groupBuyClient.join(any(GroupJoinCO.class)))
                .thenThrow(new BizException(ErrorCode.PARAM_ERROR, "该团已满员"));
        stubClosureSuccess();

        assertDoesNotThrow(() -> service.participateAfterPayment(ORDER_NO));

        verify(orderRepository).cancelPaidBySystem(ORDER_NO);
        ArgumentCaptor<List<StockOpCO>> stockCaptor = ArgumentCaptor.forClass(List.class);
        verify(skuClient).restoreStock(stockCaptor.capture());
        assertEquals(SKU_BUSINESS_ID, stockCaptor.getValue().get(0).getSkuId());
        ArgumentCaptor<OrderRefundCO> refundCaptor = ArgumentCaptor.forClass(OrderRefundCO.class);
        verify(orderRefundClient).refundByOrder(refundCaptor.capture());
        assertEquals(ORDER_NO, refundCaptor.getValue().getOrderNo());
        assertEquals("GB:" + ORDER_NO, refundCaptor.getValue().getSceneRef(), "幂等锚点 = GB:{orderNo}");
        assertEquals("拼团参团失败自动退款", refundCaptor.getValue().getReason());
    }

    @Test
    @DisplayName("participateAfterPayment — 参团上下文缺失（过期/丢失/Redis 故障）：走退款闭环")
    void shouldTriggerRefundClosure_whenContextMissing() {
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(order(OrderTypeEnum.GROUP_BUY.getCode()));
        when(joinContextStore.take(ORDER_NO)).thenReturn(null);
        stubClosureSuccess();

        assertDoesNotThrow(() -> service.participateAfterPayment(ORDER_NO));

        verify(groupBuyClient, never()).join(any());
        verify(orderRepository).cancelPaidBySystem(ORDER_NO);
        verify(skuClient).restoreStock(anyList());
        verify(orderRefundClient).refundByOrder(any(OrderRefundCO.class));
    }

    @Test
    @DisplayName("participateAfterPayment — I55 退款命令失败：吞掉告警不外抛（不向 pay 回调方抛异常）")
    void shouldNotThrow_whenRefundCommandFails() {
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(order(OrderTypeEnum.GROUP_BUY.getCode()));
        when(joinContextStore.take(ORDER_NO)).thenReturn(ctx(1L, "T-1"));
        when(groupBuyClient.join(any(GroupJoinCO.class)))
                .thenThrow(new BizException(ErrorCode.SYSTEM_BUSY, "拼团服务暂不可用"));
        stubClosureSuccess();
        when(orderRefundClient.refundByOrder(any(OrderRefundCO.class)))
                .thenThrow(new BizException(ErrorCode.SYSTEM_BUSY, "退款服务暂不可用"));

        assertDoesNotThrow(() -> service.participateAfterPayment(ORDER_NO));

        verify(orderRepository).cancelPaidBySystem(ORDER_NO);
    }

    @Test
    @DisplayName("participateAfterPayment — 条件取消 0 行（订单已非待发货）：跳过恢复库存与退款")
    void shouldSkipClosure_whenCancelMisses() {
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(order(OrderTypeEnum.GROUP_BUY.getCode()));
        when(joinContextStore.take(ORDER_NO)).thenReturn(ctx(1L, "T-1"));
        when(groupBuyClient.join(any(GroupJoinCO.class)))
                .thenThrow(new BizException(ErrorCode.PARAM_ERROR, "该团已结"));
        when(orderRepository.cancelPaidBySystem(ORDER_NO)).thenReturn(false);

        service.participateAfterPayment(ORDER_NO);

        verify(skuClient, never()).restoreStock(anyList());
        verifyNoInteractions(orderRefundClient);
    }

    // ==================== I54 结团退款联动 ====================

    @Test
    @DisplayName("refundFailedTeam — 逐单处理：单笔异常不中断整团，其余订单继续闭环")
    void shouldProcessEachOrderIndependently_whenTeamRefund() {
        Order first = order(OrderTypeEnum.GROUP_BUY.getCode());
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(first);
        when(orderRepository.cancelPaidBySystem(ORDER_NO)).thenReturn(true);
        when(orderRepository.findItemsByOrderId(1L)).thenReturn(List.of(item()));
        when(orderRepository.findByOrderNo("GB-ORD-2")).thenThrow(new RuntimeException("db glitch"));

        GroupTeamRefundCO refund = new GroupTeamRefundCO("T-1", List.of(ORDER_NO, "GB-ORD-2"));
        assertDoesNotThrow(() -> service.refundFailedTeam(refund));

        verify(orderRefundClient, times(1)).refundByOrder(any(OrderRefundCO.class));
        verify(orderRepository).findByOrderNo("GB-ORD-2");
    }

    @Test
    @DisplayName("refundFailedTeam — 已取消/非待发货订单：条件取消 0 行跳过闭环，继续其余订单")
    void shouldSkipNonPendingShipment_whenTeamRefund() {
        when(orderRepository.findByOrderNo("GB-A")).thenReturn(order(OrderTypeEnum.GROUP_BUY.getCode()));
        when(orderRepository.cancelPaidBySystem("GB-A")).thenReturn(false);
        Order second = order(OrderTypeEnum.GROUP_BUY.getCode());
        second.setId(2L);
        second.setOrderNo("GB-B");
        when(orderRepository.findByOrderNo("GB-B")).thenReturn(second);
        when(orderRepository.cancelPaidBySystem("GB-B")).thenReturn(true);
        when(orderRepository.findItemsByOrderId(2L)).thenReturn(List.of(item()));

        service.refundFailedTeam(new GroupTeamRefundCO("T-1", List.of("GB-A", "GB-B")));

        verify(skuClient, times(1)).restoreStock(anyList());
        verify(orderRefundClient, times(1)).refundByOrder(any(OrderRefundCO.class));
    }

    @Test
    @DisplayName("refundFailedTeam — 订单不存在的成员行：跳过并继续")
    void shouldSkipMissingOrder_whenTeamRefund() {
        when(orderRepository.findByOrderNo("GB-A")).thenReturn(null);
        Order second = order(OrderTypeEnum.GROUP_BUY.getCode());
        second.setOrderNo("GB-B");
        when(orderRepository.findByOrderNo("GB-B")).thenReturn(second);
        when(orderRepository.cancelPaidBySystem("GB-B")).thenReturn(true);
        when(orderRepository.findItemsByOrderId(1L)).thenReturn(List.of(item()));

        service.refundFailedTeam(new GroupTeamRefundCO("T-1", List.of("GB-A", "GB-B")));

        verify(orderRefundClient, times(1)).refundByOrder(any(OrderRefundCO.class));
        verify(orderRepository).cancelPaidBySystem("GB-B");
        verify(orderRepository, never()).cancelPaidBySystem("GB-A");
    }

    @Test
    @DisplayName("refundFailedTeam — 订单号列表为空：直接返回零动作")
    void shouldSkip_whenOrderNosEmpty() {
        service.refundFailedTeam(new GroupTeamRefundCO("T-1", List.of()));

        verifyNoInteractions(orderRepository, skuClient, groupBuyClient, orderRefundClient);
    }
}
