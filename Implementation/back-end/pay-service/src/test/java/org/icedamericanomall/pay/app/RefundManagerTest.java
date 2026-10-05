package org.icedamericanomall.pay.app;

import org.icedamericanomall.client.AfterSaleClient;
import org.icedamericanomall.client.BalanceClient;
import org.icedamericanomall.client.NotificationClient;
import org.icedamericanomall.client.TradeInvoiceClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.BalanceRefundCO;
import org.icedamericanomall.dto.InAppMessageSendReqCO;
import org.icedamericanomall.dto.InvoiceVoidCO;
import org.icedamericanomall.dto.RefundCreateCO;
import org.icedamericanomall.dto.RefundResultCO;
import org.icedamericanomall.integration.payment.ChannelRefundResult;
import org.icedamericanomall.integration.payment.WechatRefundCallbackResult;
import org.icedamericanomall.pay.adapter.assembler.RefundConverterImpl;
import org.icedamericanomall.pay.adapter.response.RefundVO;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.domain.model.refundorder.RefundOrder;
import org.icedamericanomall.pay.domain.enums.RefundStatusEnum;
import org.icedamericanomall.pay.domain.repository.PayOrderRepository;
import org.icedamericanomall.pay.domain.repository.RefundOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * RefundManager 退款编排单元测试（F1 买家退款资金流 BDD）。
 *
 * <p>覆盖契约 §4.7.3 的五条 Scenario：余额渠道退款+幂等、微信回调激活 REFUNDED、
 * 同售后单重复请求幂等、失败停留 APPROVED 可重试（含 3025）、退款金额超实付被拒。
 * Feign（余额/渠道/售后回写）与领域服务全部打桩；出参装配用真实 MapStruct 转换器
 * （{@code RefundConverterImpl}），锁死 Domain → CO/VO 的字段子集口径。
 * 退款成功收口走真实 {@link PayEventNotifier}（ADR-042），锁死「I42 回写 + 买家触达」到 Feign 客户端。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RefundManager 退款编排单元测试（F1）")
class RefundManagerTest {

    private static final String REFUND_NO = "RFD-1";
    private static final String PAY_ORDER_NO = "PAY-1";
    private static final String ORDER_NO = "ORD-1";
    private static final long AFTER_SALE_ID = 9L;
    private static final int AMOUNT = 12800;

    @Mock RefundOrderRepository refundOrderService;
    @Mock PayOrderRepository payOrderService;
    @Mock PaymentChannelRouter channelRouter;
    @Mock BalanceClient balanceClient;
    @Mock AfterSaleClient afterSaleClient;
    @Mock NotificationClient notificationClient;
    @Mock TradeInvoiceClient tradeInvoiceClient;

    private RefundManager refundManager;

    @BeforeEach
    void setUp() {
        refundManager = new RefundManager(refundOrderService, payOrderService, channelRouter,
                balanceClient, new RefundConverterImpl(),
                new PayEventNotifier(notificationClient, afterSaleClient, tradeInvoiceClient, payOrderService));
    }

    private RefundCreateCO request() {
        RefundCreateCO req = new RefundCreateCO();
        req.setAfterSaleId(AFTER_SALE_ID);
        req.setOrderNo(ORDER_NO);
        req.setRefundAmount(AMOUNT);
        req.setReason("质量问题");
        return req;
    }

    private PayOrder payOrder(String channel, int amount) {
        PayOrder payOrder = new PayOrder();
        payOrder.setId(1L);
        payOrder.setPayOrderNo(PAY_ORDER_NO);
        payOrder.setBizOrderNo(ORDER_NO);
        payOrder.setBizUserId(100L);
        payOrder.setPayChannelCode(channel);
        payOrder.setAmount(amount);
        return payOrder;
    }

    private RefundOrder refundOrder(int status, String channel) {
        RefundOrder entity = new RefundOrder();
        entity.setId(1L);
        entity.setRefundNo(REFUND_NO);
        entity.setAfterSaleId(AFTER_SALE_ID);
        entity.setOrderNo(ORDER_NO);
        entity.setPayOrderNo(PAY_ORDER_NO);
        entity.setUserId(100L);
        entity.setChannel(channel);
        entity.setRefundAmount(AMOUNT);
        entity.setStatus(status);
        return entity;
    }

    /** 已置成功的售后退款单（补齐 bizRef=AS:{afterSaleId}，I56 联动入参口径）。 */
    private RefundOrder settledRefundOrder(String channel) {
        RefundOrder settled = refundOrder(1, channel);
        settled.setBizRef(RefundOrder.afterSaleBizRef(AFTER_SALE_ID));
        return settled;
    }

    // ==================== Scenario 1：余额渠道退款成功（同步入账 + 回写售后单） ====================

    @Test
    @DisplayName("余额渠道 — 调 I41 入账 → 退款单置成功 → I42 回写售后单 REFUNDED")
    void shouldRefundByBalance_whenAfterSaleApproved() {
        when(refundOrderService.getByAfterSaleId(AFTER_SALE_ID)).thenReturn(null);
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(payOrder("BALANCE", AMOUNT));
        when(refundOrderService.createProcessing(eq(AFTER_SALE_ID), eq(ORDER_NO), eq(PAY_ORDER_NO),
                eq(100L), eq("BALANCE"), eq(AMOUNT))).thenReturn(refundOrder(0, "BALANCE"));
        when(refundOrderService.markSuccessIfProcessing(eq(REFUND_NO), any())).thenReturn(true);
        when(refundOrderService.getByRefundNo(REFUND_NO)).thenReturn(refundOrder(1, "BALANCE"));

        RefundResultCO result = refundManager.refund(request());

        assertEquals(RefundStatusEnum.SUCCESS.getCode(), result.getStatus());
        // I41：transactionNo = refundNo（幂等键），金额/用户沿用退款单与支付单
        ArgumentCaptor<BalanceRefundCO> captor = ArgumentCaptor.forClass(BalanceRefundCO.class);
        verify(balanceClient).refund(captor.capture());
        assertEquals(REFUND_NO, captor.getValue().getTransactionNo());
        assertEquals(100L, captor.getValue().getUserId());
        assertEquals(AMOUNT, captor.getValue().getAmount());
        assertEquals(REFUND_NO, captor.getValue().getBizNo());
        // I42：成功后回写售后单
        verify(afterSaleClient).markRefunded(AFTER_SALE_ID);
    }

    @Test
    @DisplayName("余额渠道 — 渠道未走微信/支付宝路由（余额走 I41 本地入账）")
    void shouldNotCallChannelRouter_whenBalance() {
        when(refundOrderService.getByAfterSaleId(AFTER_SALE_ID)).thenReturn(null);
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(payOrder("BALANCE", AMOUNT));
        when(refundOrderService.createProcessing(any(), anyString(), anyString(), any(), anyString(), anyInt()))
                .thenReturn(refundOrder(0, "BALANCE"));
        when(refundOrderService.markSuccessIfProcessing(anyString(), any())).thenReturn(true);
        when(refundOrderService.getByRefundNo(REFUND_NO)).thenReturn(refundOrder(1, "BALANCE"));

        refundManager.refund(request());

        verifyNoInteractions(channelRouter);
    }

    // ==================== Scenario 2：微信渠道处理中 + 回调激活 REFUNDED ====================

    @Test
    @DisplayName("微信渠道 — 渠道受理处理中：退款单保持处理中，不回写售后单（等回调）")
    void shouldStayProcessing_whenWechatAccepted() {
        when(refundOrderService.getByAfterSaleId(AFTER_SALE_ID)).thenReturn(null);
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(payOrder("WECHAT", AMOUNT));
        when(refundOrderService.createProcessing(any(), anyString(), anyString(), any(), anyString(), anyInt()))
                .thenReturn(refundOrder(0, "WECHAT"));
        when(channelRouter.refund(any(), eq(PAY_ORDER_NO), eq(REFUND_NO), eq(AMOUNT), eq(AMOUNT), any()))
                .thenReturn(ChannelRefundResult.processing("ch-rfd-1"));
        when(refundOrderService.getByRefundNo(REFUND_NO)).thenReturn(refundOrder(0, "WECHAT"));

        RefundResultCO result = refundManager.refund(request());

        assertEquals(RefundStatusEnum.PROCESSING.getCode(), result.getStatus());
        verify(refundOrderService, never()).markSuccessIfProcessing(anyString(), any());
        verify(afterSaleClient, never()).markRefunded(any());
    }

    @Test
    @DisplayName("微信退款回调成功 — 条件置成功 + 回填渠道退款单号 + I42 回写售后单")
    void shouldConfirmRefunded_whenWechatRefundCallbackSuccess() {
        when(channelRouter.parseWechatRefundCallback(any(), anyString()))
                .thenReturn(new WechatRefundCallbackResult(REFUND_NO, true, "5000099", "SUCCESS"));
        when(refundOrderService.getByRefundNo(REFUND_NO)).thenReturn(refundOrder(0, "WECHAT"));
        when(refundOrderService.markSuccessIfProcessing(REFUND_NO, "5000099")).thenReturn(true);

        refundManager.handleWechatRefundCallback(Map.of("Wechatpay-Signature", "sig"), "{\"resource\":{}}");

        verify(refundOrderService).markSuccessIfProcessing(REFUND_NO, "5000099");
        verify(afterSaleClient).markRefunded(AFTER_SALE_ID);
    }

    @Test
    @DisplayName("退款成功触达 — 回调置成功：买家收到 REFUND_SUCCESS 站内信（订单号+退款金额+原路渠道）")
    void shouldNotifyBuyerRefundSuccess_whenWechatCallbackConfirmed() {
        when(channelRouter.parseWechatRefundCallback(any(), anyString()))
                .thenReturn(new WechatRefundCallbackResult(REFUND_NO, true, "5000099", "SUCCESS"));
        when(refundOrderService.getByRefundNo(REFUND_NO)).thenReturn(refundOrder(0, "WECHAT"));
        when(refundOrderService.markSuccessIfProcessing(REFUND_NO, "5000099")).thenReturn(true);

        refundManager.handleWechatRefundCallback(Map.of(), "{}");

        ArgumentCaptor<InAppMessageSendReqCO> captor = ArgumentCaptor.forClass(InAppMessageSendReqCO.class);
        verify(notificationClient).sendInAppMessage(captor.capture());
        InAppMessageSendReqCO message = captor.getValue();
        assertEquals("REFUND_SUCCESS", message.getType());
        assertEquals(100L, message.getRecipientId());
        assertTrue(message.getContent().contains("ORD-1"), "内容须含订单号: " + message.getContent());
        assertTrue(message.getContent().contains("128.00"), "内容须含退款金额(元): " + message.getContent());
        assertTrue(message.getContent().contains("微信支付"), "内容须含原路渠道: " + message.getContent());
        assertEquals("/orders/ORD-1", message.getLinkUrl());
        // I42 回写与买家触达同路收口
        verify(afterSaleClient).markRefunded(AFTER_SALE_ID);
    }

    @Test
    @DisplayName("触达失败不侵染结算 — NotificationClient 抛异常：退款单仍置成功、售后单仍回写")
    void shouldStillSettleRefund_whenBuyerNotificationFails() {
        doThrow(new RuntimeException("notification-service down"))
                .when(notificationClient).sendInAppMessage(any(InAppMessageSendReqCO.class));
        when(refundOrderService.getByAfterSaleId(AFTER_SALE_ID)).thenReturn(null);
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(payOrder("BALANCE", AMOUNT));
        when(refundOrderService.createProcessing(any(), anyString(), anyString(), any(), anyString(), anyInt()))
                .thenReturn(refundOrder(0, "BALANCE"));
        when(refundOrderService.markSuccessIfProcessing(anyString(), any())).thenReturn(true);
        when(refundOrderService.getByRefundNo(REFUND_NO)).thenReturn(refundOrder(1, "BALANCE"));

        assertDoesNotThrow(() -> refundManager.refund(request()));

        verify(refundOrderService).markSuccessIfProcessing(eq(REFUND_NO), any());
        verify(afterSaleClient).markRefunded(AFTER_SALE_ID);
    }

    @Test
    @DisplayName("收件人缺失 — 退款单无 userId：不发买家站内信，I42 回写照常")
    void shouldSkipBuyerMessage_whenRecipientMissing() {
        RefundOrder noBuyer = refundOrder(0, "WECHAT");
        noBuyer.setUserId(null);
        when(channelRouter.parseWechatRefundCallback(any(), anyString()))
                .thenReturn(new WechatRefundCallbackResult(REFUND_NO, true, "5000099", "SUCCESS"));
        when(refundOrderService.getByRefundNo(REFUND_NO)).thenReturn(noBuyer);
        when(refundOrderService.markSuccessIfProcessing(REFUND_NO, "5000099")).thenReturn(true);

        refundManager.handleWechatRefundCallback(Map.of(), "{}");

        verifyNoInteractions(notificationClient);
        verify(afterSaleClient).markRefunded(AFTER_SALE_ID);
    }

    @Test
    @DisplayName("微信退款回调非成功终态（CLOSED）— 不改退款单状态、不回写售后单")
    void shouldIgnore_whenWechatRefundCallbackNotSuccess() {
        when(channelRouter.parseWechatRefundCallback(any(), anyString()))
                .thenReturn(new WechatRefundCallbackResult(REFUND_NO, false, "5000099", "CLOSED"));

        refundManager.handleWechatRefundCallback(Map.of(), "{}");

        verifyNoInteractions(refundOrderService);
        verifyNoInteractions(afterSaleClient);
    }

    @Test
    @DisplayName("微信退款回调重复通知 — 退款单非处理中：幂等忽略，不重复回写售后单")
    void shouldBeIdempotent_whenDuplicateRefundCallback() {
        when(channelRouter.parseWechatRefundCallback(any(), anyString()))
                .thenReturn(new WechatRefundCallbackResult(REFUND_NO, true, "5000099", "SUCCESS"));
        when(refundOrderService.getByRefundNo(REFUND_NO)).thenReturn(refundOrder(1, "WECHAT"));
        when(refundOrderService.markSuccessIfProcessing(REFUND_NO, "5000099")).thenReturn(false);

        refundManager.handleWechatRefundCallback(Map.of(), "{}");

        verify(afterSaleClient, never()).markRefunded(any());
    }

    @Test
    @DisplayName("微信退款回调 — 退款单不存在：抛 ORDER_NOT_FOUND（控制器转 500 触发渠道重推）")
    void shouldThrow_whenRefundOrderMissingOnCallback() {
        when(channelRouter.parseWechatRefundCallback(any(), anyString()))
                .thenReturn(new WechatRefundCallbackResult("UNKNOWN", true, "5000099", "SUCCESS"));
        when(refundOrderService.getByRefundNo("UNKNOWN")).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> refundManager.handleWechatRefundCallback(Map.of(), "{}"));

        assertEquals(ErrorCode.ORDER_NOT_FOUND, ex.getErrorCode());
    }

    // ==================== Scenario 3：同售后单重复请求幂等 ====================

    @Test
    @DisplayName("同售后单重复请求 — 返回既有退款单，不新建、不再发起渠道退款")
    void shouldReturnExisting_whenSameAfterSaleId() {
        when(refundOrderService.getByAfterSaleId(AFTER_SALE_ID)).thenReturn(refundOrder(0, "WECHAT"));

        RefundResultCO result = refundManager.refund(request());

        assertEquals(REFUND_NO, result.getRefundNo());
        verify(refundOrderService, never()).createProcessing(any(), any(), any(), any(), any(), anyInt());
        verifyNoInteractions(payOrderService);
        verifyNoInteractions(channelRouter);
    }

    // ==================== Scenario 4：失败停留 APPROVED + 可重试（含 3025） ====================

    @Test
    @DisplayName("渠道退款失败 — 退款单置失败并记录原因，异常透出（售后单停留 APPROVED，不回写 REFUNDED）")
    void shouldMarkFailed_whenChannelRefundFails() {
        when(refundOrderService.getByAfterSaleId(AFTER_SALE_ID)).thenReturn(null);
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(payOrder("WECHAT", AMOUNT));
        when(refundOrderService.createProcessing(any(), anyString(), anyString(), any(), anyString(), anyInt()))
                .thenReturn(refundOrder(0, "WECHAT"));
        when(channelRouter.refund(any(), anyString(), anyString(), anyInt(), anyInt(), any()))
                .thenThrow(new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "微信退款申请失败"));

        BizException ex = assertThrows(BizException.class, () -> refundManager.refund(request()));

        assertEquals(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, ex.getErrorCode());
        verify(refundOrderService).markFailedIfProcessing(REFUND_NO, "微信退款申请失败");
        verify(afterSaleClient, never()).markRefunded(any());
    }

    @Test
    @DisplayName("管理端重试 — 失败单：重置处理中 → 重新发起渠道退款（余额渠道幂等入账）")
    void shouldRetry_whenRefundFailed() {
        // 首次查询为失败单（重试前置条件），后续查询返回重试后的成功态
        when(refundOrderService.getByRefundNo(REFUND_NO))
                .thenReturn(refundOrder(2, "BALANCE"), refundOrder(1, "BALANCE"));
        when(payOrderService.getByPayOrderNo(PAY_ORDER_NO)).thenReturn(payOrder("BALANCE", AMOUNT));
        when(refundOrderService.markProcessingIfFailed(REFUND_NO)).thenReturn(true);
        when(refundOrderService.markSuccessIfProcessing(eq(REFUND_NO), any())).thenReturn(true);

        RefundVO result = refundManager.retry(REFUND_NO);

        assertEquals(RefundStatusEnum.SUCCESS.getCode(), result.getStatus());
        verify(refundOrderService).markProcessingIfFailed(REFUND_NO);
        verify(balanceClient).refund(any(BalanceRefundCO.class));
        verify(afterSaleClient).markRefunded(AFTER_SALE_ID);
    }

    @Test
    @DisplayName("管理端重试 — 非失败状态（处理中）→ REFUND_STATUS_INVALID(3039)")
    void shouldRejectRetry_whenRefundNotFailed() {
        when(refundOrderService.getByRefundNo(REFUND_NO)).thenReturn(refundOrder(0, "WECHAT"));

        BizException ex = assertThrows(BizException.class, () -> refundManager.retry(REFUND_NO));

        assertEquals(ErrorCode.REFUND_STATUS_INVALID, ex.getErrorCode());
        verify(refundOrderService, never()).markProcessingIfFailed(anyString());
    }

    @Test
    @DisplayName("管理端重试 — 已成功单 → REFUND_STATUS_INVALID(3039)")
    void shouldRejectRetry_whenRefundAlreadySuccess() {
        when(refundOrderService.getByRefundNo(REFUND_NO)).thenReturn(refundOrder(1, "WECHAT"));

        BizException ex = assertThrows(BizException.class, () -> refundManager.retry(REFUND_NO));

        assertEquals(ErrorCode.REFUND_STATUS_INVALID, ex.getErrorCode());
    }

    @Test
    @DisplayName("管理端重试 — 退款单不存在 → NO_RESOURCE_FOUND")
    void shouldRejectRetry_whenRefundNotFound() {
        when(refundOrderService.getByRefundNo("MISSING")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> refundManager.retry("MISSING"));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
    }

    // ==================== Scenario 5：退款金额超实付 / 支付单缺失 ====================

    @Test
    @DisplayName("退款金额超过实付 — PARAM_ERROR（双保险校验，不创建退款单）")
    void shouldReject_whenRefundAmountExceedsPaid() {
        RefundCreateCO req = request();
        req.setRefundAmount(AMOUNT + 1000);
        when(refundOrderService.getByAfterSaleId(AFTER_SALE_ID)).thenReturn(null);
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(payOrder("WECHAT", AMOUNT));

        BizException ex = assertThrows(BizException.class, () -> refundManager.refund(req));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(refundOrderService, never()).createProcessing(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("支付单不存在 — ORDER_NOT_FOUND（无支付单即无原路退回目标）")
    void shouldReject_whenPayOrderMissing() {
        when(refundOrderService.getByAfterSaleId(AFTER_SALE_ID)).thenReturn(null);
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> refundManager.refund(request()));

        assertEquals(ErrorCode.ORDER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("入参校验 — 退款金额非正数 → BadRequestException(PARAM_ERROR)")
    void shouldReject_whenRefundAmountNonPositive() {
        RefundCreateCO req = request();
        req.setRefundAmount(0);

        assertThrows(org.icedamericanomall.common.exception.BadRequestException.class,
                () -> refundManager.refund(req));
        verifyNoInteractions(refundOrderService);
    }

    @Test
    @DisplayName("并发建单（uk_after_sale 冲突）— 回落为返回既有退款单（天然幂等）")
    void shouldReturnConcurrent_whenDuplicateKeyOnCreate() {
        when(refundOrderService.getByAfterSaleId(AFTER_SALE_ID)).thenReturn(null, refundOrder(0, "WECHAT"));
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(payOrder("WECHAT", AMOUNT));
        when(refundOrderService.createProcessing(any(), anyString(), anyString(), any(), anyString(), anyInt()))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_after_sale"));

        RefundResultCO result = refundManager.refund(request());

        assertNotNull(result);
        assertEquals(REFUND_NO, result.getRefundNo());
    }

    // ==================== 全额退款发票联动（I56，PRD D4） ====================

    @Test
    @DisplayName("全额退款发票联动 — 余额同步结算成功（同步成功路径）：voidOnFullRefund 收到 orderNo+sceneRef")
    void shouldVoidInvoiceOnFullRefund_whenBalanceSettledSync() {
        when(refundOrderService.getByAfterSaleId(AFTER_SALE_ID)).thenReturn(null);
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(payOrder("BALANCE", AMOUNT));
        when(refundOrderService.createProcessing(any(), anyString(), anyString(), any(), anyString(), anyInt()))
                .thenReturn(refundOrder(0, "BALANCE"));
        when(refundOrderService.markSuccessIfProcessing(eq(REFUND_NO), any())).thenReturn(true);
        when(refundOrderService.getByRefundNo(REFUND_NO)).thenReturn(settledRefundOrder("BALANCE"));
        when(payOrderService.getByPayOrderNo(PAY_ORDER_NO)).thenReturn(payOrder("BALANCE", AMOUNT));

        refundManager.refund(request());

        ArgumentCaptor<InvoiceVoidCO> captor = ArgumentCaptor.forClass(InvoiceVoidCO.class);
        verify(tradeInvoiceClient).voidOnFullRefund(captor.capture());
        assertEquals(ORDER_NO, captor.getValue().getOrderNo());
        assertEquals(RefundOrder.afterSaleBizRef(AFTER_SALE_ID), captor.getValue().getSceneRef());
    }

    @Test
    @DisplayName("全额退款发票联动 — 微信 PAY6 回调确认成功（回调路径）：voidOnFullRefund 收到 orderNo+sceneRef")
    void shouldVoidInvoiceOnFullRefund_whenWechatCallbackConfirmed() {
        when(channelRouter.parseWechatRefundCallback(any(), anyString()))
                .thenReturn(new WechatRefundCallbackResult(REFUND_NO, true, "5000099", "SUCCESS"));
        when(refundOrderService.getByRefundNo(REFUND_NO)).thenReturn(settledRefundOrder("WECHAT"));
        when(refundOrderService.markSuccessIfProcessing(REFUND_NO, "5000099")).thenReturn(true);
        when(payOrderService.getByPayOrderNo(PAY_ORDER_NO)).thenReturn(payOrder("WECHAT", AMOUNT));

        refundManager.handleWechatRefundCallback(Map.of(), "{}");

        ArgumentCaptor<InvoiceVoidCO> captor = ArgumentCaptor.forClass(InvoiceVoidCO.class);
        verify(tradeInvoiceClient).voidOnFullRefund(captor.capture());
        assertEquals(ORDER_NO, captor.getValue().getOrderNo());
        assertEquals(RefundOrder.afterSaleBizRef(AFTER_SALE_ID), captor.getValue().getSceneRef());
    }

    @Test
    @DisplayName("全额退款发票联动 — 部分退款结算成功：不触发联动（D4 Phase 1 人工收敛）")
    void shouldNotVoidInvoice_whenPartialRefundSettled() {
        RefundCreateCO req = request();
        req.setRefundAmount(AMOUNT - 1000);
        when(refundOrderService.getByAfterSaleId(AFTER_SALE_ID)).thenReturn(null);
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(payOrder("BALANCE", AMOUNT));
        when(refundOrderService.createProcessing(any(), anyString(), anyString(), any(), anyString(), anyInt()))
                .thenReturn(refundOrder(0, "BALANCE"));
        when(refundOrderService.markSuccessIfProcessing(eq(REFUND_NO), any())).thenReturn(true);
        RefundOrder partialSettled = settledRefundOrder("BALANCE");
        partialSettled.setRefundAmount(AMOUNT - 1000);
        when(refundOrderService.getByRefundNo(REFUND_NO)).thenReturn(partialSettled);
        when(payOrderService.getByPayOrderNo(PAY_ORDER_NO)).thenReturn(payOrder("BALANCE", AMOUNT));

        refundManager.refund(req);

        verifyNoInteractions(tradeInvoiceClient);
    }

    @Test
    @DisplayName("全额退款发票联动 — 联动抛异常 fail-open：退款单仍置成功、售后单仍回写")
    void shouldStillSettleRefund_whenInvoiceVoidFails() {
        doThrow(new RuntimeException("trade-service down"))
                .when(tradeInvoiceClient).voidOnFullRefund(any(InvoiceVoidCO.class));
        when(refundOrderService.getByAfterSaleId(AFTER_SALE_ID)).thenReturn(null);
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(payOrder("BALANCE", AMOUNT));
        when(refundOrderService.createProcessing(any(), anyString(), anyString(), any(), anyString(), anyInt()))
                .thenReturn(refundOrder(0, "BALANCE"));
        when(refundOrderService.markSuccessIfProcessing(eq(REFUND_NO), any())).thenReturn(true);
        when(refundOrderService.getByRefundNo(REFUND_NO)).thenReturn(settledRefundOrder("BALANCE"));
        when(payOrderService.getByPayOrderNo(PAY_ORDER_NO)).thenReturn(payOrder("BALANCE", AMOUNT));

        RefundResultCO result = refundManager.refund(request());

        assertEquals(RefundStatusEnum.SUCCESS.getCode(), result.getStatus());
        verify(afterSaleClient).markRefunded(AFTER_SALE_ID);
    }
}
