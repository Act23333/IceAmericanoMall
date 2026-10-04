package org.icedamericanomall.trade.app.aftersale;

import org.icedamericanomall.client.NotificationClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.exception.ForbiddenException;
import org.icedamericanomall.dto.InAppMessageSendReqCO;
import org.icedamericanomall.trade.adapter.assembler.TradeConverterImpl;
import org.icedamericanomall.trade.adapter.request.AfterSaleApplyReq;
import org.icedamericanomall.trade.adapter.response.AfterSaleVO;
import org.icedamericanomall.trade.app.TradeEventNotifier;
import org.icedamericanomall.trade.domain.enums.AfterSaleStatusEnum;
import org.icedamericanomall.trade.domain.enums.AfterSaleTypeEnum;
import org.icedamericanomall.trade.domain.enums.OrderStatusEnum;
import org.icedamericanomall.trade.domain.model.aftersale.AfterSale;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.repository.AfterSaleRepository;
import org.icedamericanomall.trade.domain.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AfterSaleManager 单元测试 —— 售后申请校验（订单存在/归属/状态/金额）、审核状态合法性、
 * 条件迁移（确认收货 / 退款回写）与退款触发时机（F1，V5.6 纠正，ADR-037）。
 *
 * <p>锁死三条退款触发不变量：
 * <ol>
 *   <li><b>仅退款（type=2）</b>在审核同意时触发退款；<b>退货退款（type=1）</b>审核同意时<b>不</b>触发；</li>
 *   <li>确认收到退货（含幂等重复确认）后触发退款（pay-service 侧 uk_after_sale 兜底）；</li>
 *   <li>触发发生在状态落库<b>之后</b>（事务外 Feign）。</li>
 * </ol>
 *
 * <p>审核结果买家触达（ADR-042）经真实 {@link TradeEventNotifier} 打通到 NotificationClient。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AfterSaleManager 售后编排单元测试")
class AfterSaleManagerTest {

    @Mock private OrderRepository orderRepository;
    @Mock private AfterSaleRepository afterSaleRepository;
    @Mock private RefundManager refundManager;
    @Mock private NotificationClient notificationClient;

    private AfterSaleManager manager;

    @BeforeEach
    void setUp() {
        manager = new AfterSaleManager(afterSaleRepository, orderRepository,
                refundManager, new TradeConverterImpl(), new TradeEventNotifier(notificationClient));
    }

    private Order order(int status, int payAmount) {
        Order o = new Order();
        o.setId(1L);
        o.setOrderNo("ORD-1");
        o.setUserId(100L);
        o.setPayAmount(payAmount);
        o.setStatus(status);
        return o;
    }

    private AfterSaleApplyReq req(Integer refundAmount) {
        AfterSaleApplyReq r = new AfterSaleApplyReq();
        r.setOrderNo("ORD-1");
        r.setType(1);
        r.setReason("质量问题");
        r.setRefundAmount(refundAmount);
        return r;
    }

    // ==================== 售后申请 ====================

    @Test
    @DisplayName("apply — 订单不存在：抛 ORDER_NOT_FOUND")
    void shouldReject_whenOrderNotFound() {
        when(orderRepository.findByOrderNo("ORD-1")).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> manager.apply(100L, req(100)));
        assertEquals(ErrorCode.ORDER_NOT_FOUND, ex.getErrorCode());
        verify(afterSaleRepository, never()).save(any(AfterSale.class));
    }

    @Test
    @DisplayName("apply — 非本人订单：抛 FORBIDDEN")
    void shouldReject_whenOrderBelongsToAnotherUser() {
        when(orderRepository.findByOrderNo("ORD-1"))
                .thenReturn(order(OrderStatusEnum.PENDING_RECEIPT.getCode(), 10000));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> manager.apply(999L, req(100)));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(afterSaleRepository, never()).save(any(AfterSale.class));
    }

    @Test
    @DisplayName("apply — 订单状态不允许售后（待付款）：抛 ORDER_STATUS_INVALID")
    void shouldReject_whenOrderStatusNotAfterSaleable() {
        when(orderRepository.findByOrderNo("ORD-1"))
                .thenReturn(order(OrderStatusEnum.PENDING_PAYMENT.getCode(), 10000));

        BizException ex = assertThrows(BizException.class,
                () -> manager.apply(100L, req(100)));
        assertEquals(ErrorCode.ORDER_STATUS_INVALID, ex.getErrorCode());
    }

    @Test
    @DisplayName("apply — 退款金额超过实付金额：抛 PARAM_ERROR")
    void shouldReject_whenRefundExceedsPayAmount() {
        when(orderRepository.findByOrderNo("ORD-1"))
                .thenReturn(order(OrderStatusEnum.COMPLETED.getCode(), 5000));

        BizException ex = assertThrows(BizException.class,
                () -> manager.apply(100L, req(5001)));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("apply — 退款金额非正数：抛 PARAM_ERROR")
    void shouldReject_whenRefundAmountNotPositive() {
        when(orderRepository.findByOrderNo("ORD-1"))
                .thenReturn(order(OrderStatusEnum.COMPLETED.getCode(), 5000));

        BizException ex = assertThrows(BizException.class,
                () -> manager.apply(100L, req(0)));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("apply — 校验通过：落库一条待审核售后单")
    void shouldSavePendingAfterSale_whenValid() {
        when(orderRepository.findByOrderNo("ORD-1"))
                .thenReturn(order(OrderStatusEnum.PENDING_RECEIPT.getCode(), 5000));
        when(afterSaleRepository.existsByOrderNoAndUserId("ORD-1", 100L)).thenReturn(false);
        when(afterSaleRepository.save(any(AfterSale.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AfterSaleVO vo = manager.apply(100L, req(3000));

        assertEquals(AfterSaleStatusEnum.PENDING.getCode(), vo.getStatus());
        assertEquals(3000, vo.getRefundAmount());
        ArgumentCaptor<AfterSale> captor = ArgumentCaptor.forClass(AfterSale.class);
        verify(afterSaleRepository).save(captor.capture());
        assertEquals(AfterSaleStatusEnum.PENDING.getCode(), captor.getValue().getStatus());
        assertEquals(3000, captor.getValue().getRefundAmount());
    }

    @Test
    @DisplayName("apply — 重复申请：抛 AFTER_SALE_EXISTS")
    void shouldReject_whenDuplicateApplication() {
        when(orderRepository.findByOrderNo("ORD-1"))
                .thenReturn(order(OrderStatusEnum.COMPLETED.getCode(), 5000));
        when(afterSaleRepository.existsByOrderNoAndUserId("ORD-1", 100L)).thenReturn(true);

        BizException ex = assertThrows(BizException.class,
                () -> manager.apply(100L, req(100)));
        assertEquals(ErrorCode.AFTER_SALE_EXISTS, ex.getErrorCode());
    }

    @Test
    @DisplayName("apply — 售后类型非法：抛 PARAM_ERROR 且不落库（领域兜底）")
    void shouldReject_whenAfterSaleTypeIllegal() {
        AfterSaleApplyReq r = req(100);
        r.setType(9);

        BizException ex = assertThrows(BizException.class, () -> manager.apply(100L, r));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(afterSaleRepository, never()).save(any(AfterSale.class));
    }

    // ==================== 售后审核（含退款触发时机） ====================

    @Test
    @DisplayName("review — 非法状态值：抛 PARAM_ERROR")
    void shouldRejectReview_whenStatusIllegal() {
        BizException ex = assertThrows(BizException.class,
                () -> manager.review(1L, 99, "备注"));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(afterSaleRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("review — 售后单不存在：抛 APPLICATION_NOT_FOUND")
    void shouldRejectReview_whenEntityMissing() {
        when(afterSaleRepository.findById(1L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> manager.review(1L, AfterSaleStatusEnum.APPROVED.getCode(), "同意"));
        assertEquals(ErrorCode.APPLICATION_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("review — 已退款（REFUNDED）单不可再审核：抛 REFUND_STATUS_INVALID（防「撤销退款」）")
    void shouldRejectReview_whenAlreadyRefunded() {
        AfterSale refunded = new AfterSale();
        refunded.setId(1L);
        refunded.setStatus(AfterSaleStatusEnum.REFUNDED.getCode());
        when(afterSaleRepository.findById(1L)).thenReturn(refunded);

        BizException ex = assertThrows(BizException.class,
                () -> manager.review(1L, AfterSaleStatusEnum.APPROVED.getCode(), "同意"));

        assertEquals(ErrorCode.REFUND_STATUS_INVALID, ex.getErrorCode());
        verify(afterSaleRepository, never()).update(any(AfterSale.class));
    }

    @Test
    @DisplayName("review — 已退货（RETURNED）单不可再审核：抛 REFUND_STATUS_INVALID（ADR-037 风险收敛）")
    void shouldRejectReview_whenAlreadyReturned() {
        AfterSale returned = new AfterSale();
        returned.setId(1L);
        returned.setStatus(AfterSaleStatusEnum.RETURNED.getCode());
        when(afterSaleRepository.findById(1L)).thenReturn(returned);

        BizException ex = assertThrows(BizException.class,
                () -> manager.review(1L, AfterSaleStatusEnum.APPROVED.getCode(), "同意"));

        assertEquals(ErrorCode.REFUND_STATUS_INVALID, ex.getErrorCode());
        verify(afterSaleRepository, never()).update(any(AfterSale.class));
        verify(refundManager, never()).triggerRefund(anyLong());
    }

    @Test
    @DisplayName("review — 审核同意 + 仅退款（type=2）：先落库（事务内）后触发退款（事务外），顺序锁死")
    void shouldTriggerRefundAfterUpdate_whenApprovedAndRefundOnly() {
        AfterSale refundOnly = new AfterSale();
        refundOnly.setId(9L);
        refundOnly.setType(AfterSaleTypeEnum.REFUND_ONLY.getCode());
        refundOnly.setStatus(AfterSaleStatusEnum.PENDING.getCode());
        when(afterSaleRepository.findById(9L)).thenReturn(refundOnly);

        manager.review(9L, AfterSaleStatusEnum.APPROVED.getCode(), "同意");

        // 顺序：先落库审核（本地），再触发退款（事务外 Feign）
        InOrder inOrder = inOrder(afterSaleRepository, refundManager);
        inOrder.verify(afterSaleRepository).update(refundOnly);
        inOrder.verify(refundManager).triggerRefund(9L);
    }

    @Test
    @DisplayName("review — 审核同意 + 退货退款（type=1）：不在审核时退款（待确认收到退货后）")
    void shouldNotTriggerRefund_whenApprovedButReturnRefund() {
        AfterSale returnRefund = new AfterSale();
        returnRefund.setId(9L);
        returnRefund.setType(AfterSaleTypeEnum.RETURN_REFUND.getCode());
        returnRefund.setStatus(AfterSaleStatusEnum.PENDING.getCode());
        when(afterSaleRepository.findById(9L)).thenReturn(returnRefund);

        manager.review(9L, AfterSaleStatusEnum.APPROVED.getCode(), "同意");

        verify(afterSaleRepository).update(returnRefund);
        verify(refundManager, never()).triggerRefund(anyLong());
    }

    @Test
    @DisplayName("review — 审核拒绝：不触发退款")
    void shouldNotTriggerRefund_whenRejected() {
        AfterSale pending = new AfterSale();
        pending.setId(9L);
        pending.setType(AfterSaleTypeEnum.REFUND_ONLY.getCode());
        pending.setStatus(AfterSaleStatusEnum.PENDING.getCode());
        when(afterSaleRepository.findById(9L)).thenReturn(pending);

        manager.review(9L, AfterSaleStatusEnum.REJECTED.getCode(), "不符");

        verify(afterSaleRepository).update(pending);
        verify(refundManager, never()).triggerRefund(anyLong());
    }

    @Test
    @DisplayName("审核触达 — 落库后买家收到 AFTER_SALE_REVIEWED 站内信（结论+备注+订单链接）")
    void shouldNotifyBuyer_whenReviewPersisted() {
        AfterSale pending = new AfterSale();
        pending.setId(9L);
        pending.setOrderNo("ORD-1");
        pending.setUserId(100L);
        pending.setType(AfterSaleTypeEnum.RETURN_REFUND.getCode());
        pending.setStatus(AfterSaleStatusEnum.PENDING.getCode());
        when(afterSaleRepository.findById(9L)).thenReturn(pending);

        manager.review(9L, AfterSaleStatusEnum.APPROVED.getCode(), "凭证已核实");

        ArgumentCaptor<InAppMessageSendReqCO> captor = ArgumentCaptor.forClass(InAppMessageSendReqCO.class);
        verify(notificationClient).sendInAppMessage(captor.capture());
        InAppMessageSendReqCO message = captor.getValue();
        assertEquals("AFTER_SALE_REVIEWED", message.getType());
        assertEquals(100L, message.getRecipientId());
        assertTrue(message.getContent().contains("同意"), "内容须含审核结论: " + message.getContent());
        assertTrue(message.getContent().contains("凭证已核实"), "内容须含审核备注: " + message.getContent());
        assertEquals("/orders/ORD-1", message.getLinkUrl());
        // 退货退款（type=1）审核同意不触发退款
        verify(refundManager, never()).triggerRefund(anyLong());
    }

    @Test
    @DisplayName("触达失败不侵染审核 — NotificationClient 抛异常：审核仍落库、无异常外溢")
    void shouldStillPersistReview_whenBuyerNotificationFails() {
        doThrow(new RuntimeException("notification-service down"))
                .when(notificationClient).sendInAppMessage(any(InAppMessageSendReqCO.class));
        AfterSale pending = new AfterSale();
        pending.setId(9L);
        pending.setOrderNo("ORD-1");
        pending.setUserId(100L);
        pending.setType(AfterSaleTypeEnum.REFUND_ONLY.getCode());
        pending.setStatus(AfterSaleStatusEnum.PENDING.getCode());
        when(afterSaleRepository.findById(9L)).thenReturn(pending);

        assertDoesNotThrow(() -> manager.review(9L, AfterSaleStatusEnum.REJECTED.getCode(), "不符"));

        verify(afterSaleRepository).update(pending);
    }

    // ==================== 退款成功回写（I42，F1） ====================

    @Test
    @DisplayName("markRefunded — 售后单不存在：抛 NO_RESOURCE_FOUND（404 原始透出）")
    void shouldRejectMarkRefunded_whenEntityMissing() {
        when(afterSaleRepository.findById(1L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> manager.markRefunded(1L));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
        verify(afterSaleRepository, never()).updateStatusConditionallyFrom(anyLong(), anyList(), anyInt());
    }

    @Test
    @DisplayName("markRefunded — 条件迁移 APPROVED(2) | RETURNED(4) → REFUNDED(5)（两条来源边共用资金终态）")
    void shouldMarkRefunded_whenApprovedOrReturned() {
        AfterSale approved = new AfterSale();
        approved.setId(1L);
        approved.setStatus(AfterSaleStatusEnum.APPROVED.getCode());
        when(afterSaleRepository.findById(1L)).thenReturn(approved);
        when(afterSaleRepository.updateStatusConditionallyFrom(eq(1L), anyList(),
                eq(AfterSaleStatusEnum.REFUNDED.getCode()))).thenReturn(1);

        manager.markRefunded(1L);

        // 仅退款走 APPROVED(2)，退货退款走 RETURNED(4)，目标同为 REFUNDED(5)
        verify(afterSaleRepository).updateStatusConditionallyFrom(1L,
                List.of(AfterSaleStatusEnum.APPROVED.getCode(), AfterSaleStatusEnum.RETURNED.getCode()), 5);
    }

    @Test
    @DisplayName("markRefunded — 非 APPROVED/RETURNED（已迁移/退款处理中停留）：幂等空操作，不抛异常")
    void shouldBeIdempotent_whenNotRefundableSource() {
        AfterSale refunded = new AfterSale();
        refunded.setId(1L);
        refunded.setStatus(AfterSaleStatusEnum.REFUNDED.getCode());
        when(afterSaleRepository.findById(1L)).thenReturn(refunded);
        when(afterSaleRepository.updateStatusConditionallyFrom(anyLong(), anyList(), anyInt())).thenReturn(0);

        assertDoesNotThrow(() -> manager.markRefunded(1L));
    }

    // ==================== 确认收到退货（V5.6，退货退款的退款触发点） ====================

    @Test
    @DisplayName("confirmReturn — 退货退款 APPROVED(2) → RETURNED(4)，落库后触发退款")
    void shouldConfirmReturn_whenReturnRefundApproved() {
        AfterSale approved = new AfterSale();
        approved.setId(1L);
        approved.setType(AfterSaleTypeEnum.RETURN_REFUND.getCode());
        approved.setStatus(AfterSaleStatusEnum.APPROVED.getCode());
        when(afterSaleRepository.findById(1L)).thenReturn(approved);
        when(afterSaleRepository.updateStatusConditionally(1L, AfterSaleStatusEnum.APPROVED.getCode(),
                AfterSaleStatusEnum.RETURNED.getCode())).thenReturn(1);

        manager.confirmReturn(1L);

        InOrder inOrder = inOrder(afterSaleRepository, refundManager);
        inOrder.verify(afterSaleRepository).updateStatusConditionally(1L, 2, 4);
        inOrder.verify(refundManager).triggerRefund(1L);
    }

    @Test
    @DisplayName("confirmReturn — 已是 RETURNED：幂等空操作不再条件更新，退款触发照常（uk_after_sale 兜底）")
    void shouldBeIdempotent_whenAlreadyReturned() {
        AfterSale returned = new AfterSale();
        returned.setId(1L);
        returned.setType(AfterSaleTypeEnum.RETURN_REFUND.getCode());
        returned.setStatus(AfterSaleStatusEnum.RETURNED.getCode());
        when(afterSaleRepository.findById(1L)).thenReturn(returned);

        assertDoesNotThrow(() -> manager.confirmReturn(1L));
        verify(afterSaleRepository, never()).updateStatusConditionally(anyLong(), anyInt(), anyInt());
        verify(refundManager).triggerRefund(1L);
    }

    @Test
    @DisplayName("confirmReturn — 已 REFUNDED：资金终态守卫抛 REFUND_STATUS_INVALID(3025)")
    void shouldRejectConfirmReturn_whenRefunded() {
        AfterSale refunded = new AfterSale();
        refunded.setId(1L);
        refunded.setType(AfterSaleTypeEnum.RETURN_REFUND.getCode());
        refunded.setStatus(AfterSaleStatusEnum.REFUNDED.getCode());
        when(afterSaleRepository.findById(1L)).thenReturn(refunded);

        BizException ex = assertThrows(BizException.class, () -> manager.confirmReturn(1L));
        assertEquals(ErrorCode.REFUND_STATUS_INVALID, ex.getErrorCode());
        verify(afterSaleRepository, never()).updateStatusConditionally(anyLong(), anyInt(), anyInt());
        verify(refundManager, never()).triggerRefund(anyLong());
    }

    @Test
    @DisplayName("confirmReturn — 仅退款（type=2）：抛 PARAM_ERROR")
    void shouldRejectConfirmReturn_whenRefundOnly() {
        AfterSale refundOnly = new AfterSale();
        refundOnly.setId(1L);
        refundOnly.setType(AfterSaleTypeEnum.REFUND_ONLY.getCode());
        refundOnly.setStatus(AfterSaleStatusEnum.APPROVED.getCode());
        when(afterSaleRepository.findById(1L)).thenReturn(refundOnly);

        BizException ex = assertThrows(BizException.class, () -> manager.confirmReturn(1L));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
    }
}
