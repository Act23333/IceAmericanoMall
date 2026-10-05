package org.icedamericanomall.pay.app;

import org.icedamericanomall.client.AfterSaleClient;
import org.icedamericanomall.client.BalanceClient;
import org.icedamericanomall.client.NotificationClient;
import org.icedamericanomall.client.TradeInvoiceClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.BalanceRefundCO;
import org.icedamericanomall.dto.InAppMessageSendReqCO;
import org.icedamericanomall.dto.InvoiceVoidCO;
import org.icedamericanomall.dto.OrderRefundCO;
import org.icedamericanomall.dto.RefundResultCO;
import org.icedamericanomall.integration.payment.ChannelRefundResult;
import org.icedamericanomall.pay.adapter.assembler.RefundConverterImpl;
import org.icedamericanomall.pay.domain.enums.PayChannelEnum;
import org.icedamericanomall.pay.domain.enums.RefundStatusEnum;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.domain.model.refundorder.RefundOrder;
import org.icedamericanomall.pay.domain.repository.PayOrderRepository;
import org.icedamericanomall.pay.domain.repository.RefundOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * OrderRefundManager 无售后单退款编排单元测试（I55，F13 拼团，ADR-049）。
 *
 * <p>覆盖契约 §4.14.15b：sceneRef 幂等重放、并发 DuplicateKey 回落、支付单全额退款金额口径、
 * 微信处理中 / 余额同步成功、支付单不存在（ORDER_NOT_FOUND 原始透出）、入参缺失（PARAM_ERROR）、
 * 渠道失败 fail-closed 置失败重抛、无售后单退款成功不回写 I42 且买家触达照常。
 * RefundManager 用真实实例（Mock 依赖 + 真实 MapStruct 转换器），锁死「I55 与 I40/PAY6 同一执行路径」。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderRefundManager 无售后单退款编排单元测试（I55，ADR-049）")
class OrderRefundManagerTest {

    private static final String REFUND_NO = "RFD-GB-1";
    private static final String PAY_ORDER_NO = "PAY-1";
    private static final String ORDER_NO = "ORD-1";
    private static final String SCENE_REF = "GB:ORD-1";
    private static final String REASON = "拼团失败自动退款";
    private static final long BUYER_ID = 100L;
    private static final int AMOUNT = 12800;

    @Mock RefundOrderRepository refundOrderService;
    @Mock PayOrderRepository payOrderService;
    @Mock PaymentChannelRouter channelRouter;
    @Mock BalanceClient balanceClient;
    @Mock AfterSaleClient afterSaleClient;
    @Mock NotificationClient notificationClient;
    @Mock TradeInvoiceClient tradeInvoiceClient;

    private OrderRefundManager orderRefundManager;

    @BeforeEach
    void setUp() {
        RefundManager refundManager = new RefundManager(refundOrderService, payOrderService, channelRouter,
                balanceClient, new RefundConverterImpl(),
                new PayEventNotifier(notificationClient, afterSaleClient, tradeInvoiceClient, payOrderService));
        orderRefundManager = new OrderRefundManager(refundOrderService, payOrderService, refundManager,
                new RefundConverterImpl());
    }

    private OrderRefundCO command() {
        return new OrderRefundCO(ORDER_NO, SCENE_REF, REASON);
    }

    private PayOrder payOrder(String channel, int amount) {
        PayOrder payOrder = new PayOrder();
        payOrder.setId(1L);
        payOrder.setPayOrderNo(PAY_ORDER_NO);
        payOrder.setBizOrderNo(ORDER_NO);
        payOrder.setBizUserId(BUYER_ID);
        payOrder.setPayChannelCode(channel);
        payOrder.setAmount(amount);
        return payOrder;
    }

    private RefundOrder refundOrder(int status, String channel) {
        RefundOrder entity = new RefundOrder();
        entity.setId(1L);
        entity.setRefundNo(REFUND_NO);
        entity.setAfterSaleId(null);
        entity.setBizRef(SCENE_REF);
        entity.setOrderNo(ORDER_NO);
        entity.setPayOrderNo(PAY_ORDER_NO);
        entity.setUserId(BUYER_ID);
        entity.setChannel(channel);
        entity.setRefundAmount(AMOUNT);
        entity.setStatus(status);
        return entity;
    }

    // ==================== 首次退款：建单口径 + 渠道执行 ====================

    @Test
    @DisplayName("微信首次退款 — 按支付单全额建单（biz_ref=sceneRef、afterSaleId=null）→ 渠道受理保持处理中")
    void shouldStayProcessing_whenWechatFirstRefund() {
        when(refundOrderService.getByBizRef(SCENE_REF)).thenReturn(null);
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(payOrder("WECHAT", AMOUNT));
        when(refundOrderService.createOrderRefundProcessing(anyString(), anyString(), any(), anyString(),
                anyInt(), anyString())).thenReturn(refundOrder(0, "WECHAT"));
        when(channelRouter.refund(eq(PayChannelEnum.WECHAT), eq(PAY_ORDER_NO), eq(REFUND_NO),
                eq(AMOUNT), eq(AMOUNT), eq(REASON))).thenReturn(ChannelRefundResult.processing("ch-rfd-1"));
        when(refundOrderService.getByRefundNo(REFUND_NO)).thenReturn(refundOrder(0, "WECHAT"));

        RefundResultCO result = orderRefundManager.refundByOrder(command());

        assertEquals(REFUND_NO, result.getRefundNo());
        assertEquals(RefundStatusEnum.PROCESSING.getCode(), result.getStatus());
        // 建单口径：after_sale_id=null + biz_ref=sceneRef 幂等锚点 + 金额=支付单全额（ADR-049）
        ArgumentCaptor<String> orderNoCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Integer> amountCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> bizRefCaptor = ArgumentCaptor.forClass(String.class);
        verify(refundOrderService).createOrderRefundProcessing(orderNoCaptor.capture(),
                eq(PAY_ORDER_NO), userIdCaptor.capture(), eq("WECHAT"),
                amountCaptor.capture(), bizRefCaptor.capture());
        assertEquals(ORDER_NO, orderNoCaptor.getValue());
        assertEquals(BUYER_ID, userIdCaptor.getValue());
        assertEquals(AMOUNT, amountCaptor.getValue());
        assertEquals(SCENE_REF, bizRefCaptor.getValue());
        // 无售后单：不回写 I42
        verify(afterSaleClient, never()).markRefunded(any());
        verifyNoInteractions(balanceClient);
    }

    @Test
    @DisplayName("余额首次退款 — I41 同步入账全额 → 退款单同步置成功 + 买家 REFUND_SUCCESS 站内信")
    void shouldSettleSyncSuccess_whenBalanceChannel() {
        when(refundOrderService.getByBizRef(SCENE_REF)).thenReturn(null);
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(payOrder("BALANCE", AMOUNT));
        when(refundOrderService.createOrderRefundProcessing(anyString(), anyString(), any(), anyString(),
                anyInt(), anyString())).thenReturn(refundOrder(0, "BALANCE"));
        when(refundOrderService.markSuccessIfProcessing(eq(REFUND_NO), any())).thenReturn(true);
        when(refundOrderService.getByRefundNo(REFUND_NO)).thenReturn(refundOrder(1, "BALANCE"));

        RefundResultCO result = orderRefundManager.refundByOrder(command());

        assertEquals(RefundStatusEnum.SUCCESS.getCode(), result.getStatus());
        ArgumentCaptor<BalanceRefundCO> balanceCaptor = ArgumentCaptor.forClass(BalanceRefundCO.class);
        verify(balanceClient).refund(balanceCaptor.capture());
        assertEquals(REFUND_NO, balanceCaptor.getValue().getTransactionNo());
        assertEquals(BUYER_ID, balanceCaptor.getValue().getUserId());
        assertEquals(AMOUNT, balanceCaptor.getValue().getAmount());
        // 无售后单：跳过 I42 回写，REFUND_SUCCESS 站内信照常（ADR-042/ADR-049）
        ArgumentCaptor<InAppMessageSendReqCO> messageCaptor = ArgumentCaptor.forClass(InAppMessageSendReqCO.class);
        verify(notificationClient).sendInAppMessage(messageCaptor.capture());
        assertEquals("REFUND_SUCCESS", messageCaptor.getValue().getType());
        assertEquals(BUYER_ID, messageCaptor.getValue().getRecipientId());
        verify(afterSaleClient, never()).markRefunded(any());
        verifyNoInteractions(channelRouter);
    }

    // ==================== 幂等：sceneRef 重放 / 并发建单回落 ====================

    @Test
    @DisplayName("sceneRef 重放 — 返回既有退款单，不新建、不再发起渠道退款")
    void shouldReturnExisting_whenSceneRefReplay() {
        when(refundOrderService.getByBizRef(SCENE_REF)).thenReturn(refundOrder(0, "WECHAT"));

        RefundResultCO result = orderRefundManager.refundByOrder(command());

        assertEquals(REFUND_NO, result.getRefundNo());
        verify(refundOrderService, never()).createOrderRefundProcessing(any(), any(), any(), any(),
                anyInt(), any());
        verifyNoInteractions(payOrderService, channelRouter, balanceClient);
    }

    @Test
    @DisplayName("并发建单（uk_biz_ref 冲突）— 回落为返回既有退款单（天然幂等）")
    void shouldReturnConcurrent_whenDuplicateKeyOnCreate() {
        when(refundOrderService.getByBizRef(SCENE_REF)).thenReturn(null, refundOrder(0, "WECHAT"));
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(payOrder("WECHAT", AMOUNT));
        when(refundOrderService.createOrderRefundProcessing(anyString(), anyString(), any(), anyString(),
                anyInt(), anyString())).thenThrow(new DuplicateKeyException("uk_biz_ref"));

        RefundResultCO result = orderRefundManager.refundByOrder(command());

        assertEquals(REFUND_NO, result.getRefundNo());
        verify(channelRouter, never()).refund(any(), anyString(), anyString(), anyInt(), anyInt(), any());
    }

    // ==================== 校验：支付单缺失 / 入参缺失 / 渠道失败 ====================

    @Test
    @DisplayName("支付单不存在 — ORDER_NOT_FOUND 原始透出（ADR-004），不创建退款单")
    void shouldThrowOrderNotFound_whenPayOrderMissing() {
        when(refundOrderService.getByBizRef(SCENE_REF)).thenReturn(null);
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> orderRefundManager.refundByOrder(command()));

        assertEquals(ErrorCode.ORDER_NOT_FOUND, ex.getErrorCode());
        verify(refundOrderService, never()).createOrderRefundProcessing(any(), any(), any(), any(),
                anyInt(), any());
    }

    @Test
    @DisplayName("入参缺失 — orderNo 空白 → BadRequestException(PARAM_ERROR)")
    void shouldReject_whenOrderNoBlank() {
        OrderRefundCO cmd = new OrderRefundCO("  ", SCENE_REF, REASON);

        assertThrows(BadRequestException.class, () -> orderRefundManager.refundByOrder(cmd));
        verifyNoInteractions(refundOrderService);
    }

    @Test
    @DisplayName("入参缺失 — sceneRef 空白（幂等键缺失）→ BadRequestException(PARAM_ERROR)")
    void shouldReject_whenSceneRefBlank() {
        OrderRefundCO cmd = new OrderRefundCO(ORDER_NO, "", REASON);

        assertThrows(BadRequestException.class, () -> orderRefundManager.refundByOrder(cmd));
        verifyNoInteractions(refundOrderService);
    }

    @Test
    @DisplayName("渠道退款失败 — fail-closed：退款单置失败并重抛（AD43 可重试）")
    void shouldMarkFailedAndRethrow_whenChannelRefundFails() {
        when(refundOrderService.getByBizRef(SCENE_REF)).thenReturn(null);
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(payOrder("WECHAT", AMOUNT));
        when(refundOrderService.createOrderRefundProcessing(anyString(), anyString(), any(), anyString(),
                anyInt(), anyString())).thenReturn(refundOrder(0, "WECHAT"));
        when(channelRouter.refund(any(), anyString(), anyString(), anyInt(), anyInt(), any()))
                .thenThrow(new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "微信退款申请失败"));

        BizException ex = assertThrows(BizException.class,
                () -> orderRefundManager.refundByOrder(command()));

        assertEquals(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, ex.getErrorCode());
        verify(refundOrderService).markFailedIfProcessing(REFUND_NO, "微信退款申请失败");
        verify(afterSaleClient, never()).markRefunded(any());
    }

    // ==================== 全额退款发票联动（I56，PRD D4） ====================

    @Test
    @DisplayName("全额退款发票联动 — I55 全额退款同步结算成功：voidOnFullRefund 收到 orderNo+sceneRef(GB 幂等键)")
    void shouldVoidInvoiceOnFullRefund_whenOrderRefundSettled() {
        when(refundOrderService.getByBizRef(SCENE_REF)).thenReturn(null);
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(payOrder("BALANCE", AMOUNT));
        when(refundOrderService.createOrderRefundProcessing(anyString(), anyString(), any(), anyString(),
                anyInt(), anyString())).thenReturn(refundOrder(0, "BALANCE"));
        when(refundOrderService.markSuccessIfProcessing(eq(REFUND_NO), any())).thenReturn(true);
        when(refundOrderService.getByRefundNo(REFUND_NO)).thenReturn(refundOrder(1, "BALANCE"));
        when(payOrderService.getByPayOrderNo(PAY_ORDER_NO)).thenReturn(payOrder("BALANCE", AMOUNT));

        orderRefundManager.refundByOrder(command());

        ArgumentCaptor<InvoiceVoidCO> captor = ArgumentCaptor.forClass(InvoiceVoidCO.class);
        verify(tradeInvoiceClient).voidOnFullRefund(captor.capture());
        assertEquals(ORDER_NO, captor.getValue().getOrderNo());
        assertEquals(SCENE_REF, captor.getValue().getSceneRef());
    }
}
