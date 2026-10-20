package org.icedamericanomall.pay.app;

import org.icedamericanomall.client.AfterSaleClient;
import org.icedamericanomall.client.NotificationClient;
import org.icedamericanomall.client.OrderClient;
import org.icedamericanomall.client.TradeInvoiceClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.InAppMessageSendReqCO;
import org.icedamericanomall.integration.payment.WechatCallbackResult;
import org.icedamericanomall.integration.payment.WechatRefundCallbackResult;
import org.icedamericanomall.pay.domain.enums.PayStatusEnum;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.domain.model.refundorder.RefundOrder;
import org.icedamericanomall.pay.domain.repository.InvoiceLinkCompensationRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 渠道回调编排单元测试（D 批拆类：从 {@code PayManagerTest} / {@code RefundManagerTest} 迁入）。
 *
 * <p>覆盖两条同构回调链：<b>支付回调</b>（微信整包解析 / 支付宝表单参数）与<b>退款回调</b>（PAY6）。
 * 共同判据：按<b>对外号</b>寻址 → 条件更新（受影响行数=1）→ 命中才通知下游；
 * 未命中即视为重复通知，静默且不再重复通知。
 *
 * <p><b>本类刻意区分三个号</b>：{@code ORD-1}（业务订单号）/ {@code PAY-1}（内部支付单号）/
 * {@code ctn-…}（对外渠道号）。回调只按最后一个寻址 —— 若实现回退成「回调查 pay_order_no」，
 * 本类的断言会立刻失败。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("渠道回调编排（支付回调 + 退款回调）")
class ChannelCallbackManagerTest {

    /** 业务订单号（外部交易主体，绝不作为渠道号外发）。 */
    private static final String ORDER_NO = "ORD-1";
    /** 内部支付单号（30 位示意，绝不作为渠道号外发）。 */
    private static final String PAY_ORDER_NO = "PAY-1";
    /** 对外渠道号（渠道 out_trade_no 的唯一合法来源）。 */
    private static final String CHANNEL_TRADE_NO = "ctn0000000000000001";
    /** 内部退款单号（只在本服务内定位，绝不作为渠道号外发）。 */
    private static final String REFUND_NO = "RFD-1";
    /** 对外退款项号（渠道 out_refund_no）：与内部 REFUND_NO 刻意不同，锁死「两个号已分离」。 */
    private static final String OUT_REFUND_NO = "out00000000000000001";
    private static final long AFTER_SALE_ID = 9L;
    private static final int AMOUNT = 12800;

    @Mock PayOrderRepository payOrderService;
    @Mock RefundOrderRepository refundOrderService;
    @Mock PaymentChannelRouter channelRouter;
    @Mock OrderClient orderClient;
    @Mock NotificationClient notificationClient;
    @Mock AfterSaleClient afterSaleClient;
    @Mock TradeInvoiceClient tradeInvoiceClient;

    private ChannelCallbackManager callbackManager;

    private PayOrder payOrder(int status) {
        PayOrder p = new PayOrder();
        p.setId(1L); p.setBizOrderNo(ORDER_NO); p.setPayOrderNo(PAY_ORDER_NO);
        p.setChannelTradeNo(CHANNEL_TRADE_NO); p.setStatus(status);
        return p;
    }

    private RefundOrder refundOrder(int status, String channel) {
        RefundOrder entity = new RefundOrder();
        entity.setId(1L);
        entity.setRefundNo(REFUND_NO);
        entity.setOutRefundNo(OUT_REFUND_NO);
        entity.setAfterSaleId(AFTER_SALE_ID);
        entity.setOrderNo(ORDER_NO);
        entity.setPayOrderNo(PAY_ORDER_NO);
        entity.setUserId(100L);
        entity.setChannel(channel);
        entity.setRefundAmount(AMOUNT);
        entity.setStatus(status);
        return entity;
    }

    @BeforeEach
    void setUp() {
        // 真实协作器：锁死「条件置成功 → 订单状态回写 + 买家触达」全链路到 Feign/MQ 边界
        callbackManager = new ChannelCallbackManager(payOrderService, refundOrderService, channelRouter,
                new PayEventNotifier(notificationClient, afterSaleClient, tradeInvoiceClient, payOrderService,
                        mock(InvoiceLinkCompensationRepository.class)),
                new TradeStatusNotifier(orderClient));
    }

    // ==================== 回调幂等（微信：通道适配层整包解析；支付宝：表单参数） ====================

    /** 微信回调解析结果：{@code success} 由 {@code trade_state} 判定；outTradeNo 即对外渠道号。 */
    private WechatCallbackResult wechatResult(String channelTradeNo, boolean success, String tradeState) {
        return new WechatCallbackResult(channelTradeNo, success, "TXN-1", tradeState);
    }

    @Test
    @DisplayName("handleWechatCallback — 按对外渠道号查单 + 条件更新命中：通知订单待发货")
    void shouldMarkSuccess_whenWechatCallbackParsed() {
        when(channelRouter.parseWechatCallback(anyMap(), anyString()))
                .thenReturn(wechatResult(CHANNEL_TRADE_NO, true, "SUCCESS"));
        when(payOrderService.getByChannelTradeNo(CHANNEL_TRADE_NO))
                .thenReturn(payOrder(PayStatusEnum.PENDING_PAY.getCode()));
        when(payOrderService.markSuccessIfPending(PAY_ORDER_NO, "SUCCESS")).thenReturn(true);

        callbackManager.handleWechatCallback(Map.of("Wechatpay-Signature", "sig"), "{\"id\":\"EV-1\"}");

        verify(payOrderService).getByChannelTradeNo(CHANNEL_TRADE_NO);
        verify(payOrderService).markSuccessIfPending(PAY_ORDER_NO, "SUCCESS");
        verify(orderClient).updateOrderStatus(ORDER_NO, 2);
    }

    @Test
    @DisplayName("支付成功触达 — 条件置成功命中：买家收到 PAY_SUCCESS 站内信（订单号+实付金额+订单链接）")
    void shouldNotifyBuyerPaySuccess_whenConfirmPaidHits() {
        when(channelRouter.parseWechatCallback(anyMap(), anyString()))
                .thenReturn(wechatResult(CHANNEL_TRADE_NO, true, "SUCCESS"));
        PayOrder paid = payOrder(PayStatusEnum.PENDING_PAY.getCode());
        paid.setBizUserId(100L);
        paid.setAmount(11900);
        when(payOrderService.getByChannelTradeNo(CHANNEL_TRADE_NO)).thenReturn(paid);
        when(payOrderService.markSuccessIfPending(PAY_ORDER_NO, "SUCCESS")).thenReturn(true);

        callbackManager.handleWechatCallback(Map.of(), "body");

        ArgumentCaptor<InAppMessageSendReqCO> captor = ArgumentCaptor.forClass(InAppMessageSendReqCO.class);
        verify(notificationClient).sendInAppMessage(captor.capture());
        InAppMessageSendReqCO message = captor.getValue();
        assertEquals("PAY_SUCCESS", message.getType());
        assertEquals(100L, message.getRecipientId());
        assertTrue(message.getContent().contains(ORDER_NO), "内容须含订单号: " + message.getContent());
        assertTrue(message.getContent().contains("119.00"), "内容须含实付金额(元): " + message.getContent());
        assertEquals("/orders/" + ORDER_NO, message.getLinkUrl());
    }

    @Test
    @DisplayName("触达失败不侵染主流程 — NotificationClient 抛异常：支付仍置成功、订单仍通知待发货")
    void shouldStillConfirmPaid_whenBuyerNotificationFails() {
        when(channelRouter.parseWechatCallback(anyMap(), anyString()))
                .thenReturn(wechatResult(CHANNEL_TRADE_NO, true, "SUCCESS"));
        PayOrder paid = payOrder(PayStatusEnum.PENDING_PAY.getCode());
        paid.setBizUserId(100L);
        when(payOrderService.getByChannelTradeNo(CHANNEL_TRADE_NO)).thenReturn(paid);
        when(payOrderService.markSuccessIfPending(PAY_ORDER_NO, "SUCCESS")).thenReturn(true);
        doThrow(new RuntimeException("notification-service down"))
                .when(notificationClient).sendInAppMessage(any(InAppMessageSendReqCO.class));

        assertDoesNotThrow(() -> callbackManager.handleWechatCallback(Map.of(), "body"));

        verify(payOrderService).markSuccessIfPending(PAY_ORDER_NO, "SUCCESS");
        verify(orderClient).updateOrderStatus(ORDER_NO, 2);
    }

    @Test
    @DisplayName("handleWechatCallback — 并发重复回调：条件更新 0 行，不再重复通知订单")
    void shouldSkipNotify_whenCallbackAlreadyProcessed() {
        when(channelRouter.parseWechatCallback(anyMap(), anyString()))
                .thenReturn(wechatResult(CHANNEL_TRADE_NO, true, "SUCCESS"));
        when(payOrderService.getByChannelTradeNo(CHANNEL_TRADE_NO))
                .thenReturn(payOrder(PayStatusEnum.SUCCESS.getCode()));
        when(payOrderService.markSuccessIfPending(PAY_ORDER_NO, "SUCCESS")).thenReturn(false);

        callbackManager.handleWechatCallback(Map.of(), "body");

        verify(orderClient, never()).updateOrderStatus(anyString(), anyInt());
    }

    @Test
    @DisplayName("handleWechatCallback — 非成功终态（CLOSED）：不改支付单状态、不通知订单")
    void shouldNotChangeState_whenTradeStateNotSuccess() {
        when(channelRouter.parseWechatCallback(anyMap(), anyString()))
                .thenReturn(wechatResult(CHANNEL_TRADE_NO, false, "CLOSED"));

        callbackManager.handleWechatCallback(Map.of(), "body");

        verify(payOrderService, never()).markSuccessIfPending(anyString(), any());
        verify(payOrderService, never()).getByChannelTradeNo(anyString());
        verify(orderClient, never()).updateOrderStatus(anyString(), anyInt());
    }

    @Test
    @DisplayName("handleWechatCallback — 渠道号查不到支付单：抛 NO_RESOURCE_FOUND(1103)/404 触发渠道重推")
    void shouldThrowNoResourceFound_whenPayOrderMissing() {
        when(channelRouter.parseWechatCallback(anyMap(), anyString()))
                .thenReturn(wechatResult("ctn-not-exist", true, "SUCCESS"));
        when(payOrderService.getByChannelTradeNo("ctn-not-exist")).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> callbackManager.handleWechatCallback(Map.of(), "body"));

        // U11：查的是「支付单」不是「订单」，码名必须与实体对齐（1103/404，与 3009 同状态码，调用方零感知）
        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
        assertNotEquals(ErrorCode.ORDER_NOT_FOUND, ex.getErrorCode());
        verify(orderClient, never()).updateOrderStatus(anyString(), anyInt());
    }

    @Test
    @DisplayName("handleWechatCallback — 验签/解密失败：抛 ILLEGAL_REQUEST 且不置成功")
    void shouldThrow_whenSignatureInvalid() {
        when(channelRouter.parseWechatCallback(anyMap(), anyString()))
                .thenThrow(new BizException(ErrorCode.ILLEGAL_REQUEST, "微信回调验签失败"));

        BizException ex = assertThrows(BizException.class,
                () -> callbackManager.handleWechatCallback(Map.of(), "body"));

        assertEquals(ErrorCode.ILLEGAL_REQUEST, ex.getErrorCode());
        verify(payOrderService, never()).markSuccessIfPending(anyString(), any());
    }

    @Test
    @DisplayName("handleAlipayCallback — 支付宝验签 + 表单 out_trade_no（= 对外渠道号）查单置成功")
    void shouldUseAlipayVerifier() {
        when(channelRouter.verifyAlipayCallback(anyMap())).thenReturn(true);
        when(payOrderService.getByChannelTradeNo(CHANNEL_TRADE_NO))
                .thenReturn(payOrder(PayStatusEnum.PENDING_PAY.getCode()));
        when(payOrderService.markSuccessIfPending(eq(PAY_ORDER_NO), any())).thenReturn(true);

        callbackManager.handleAlipayCallback(Map.of("out_trade_no", CHANNEL_TRADE_NO,
                "trade_status", "TRADE_SUCCESS"));

        verify(channelRouter).verifyAlipayCallback(anyMap());
        verify(payOrderService).getByChannelTradeNo(CHANNEL_TRADE_NO);
        verify(payOrderService).markSuccessIfPending(PAY_ORDER_NO, "TRADE_SUCCESS");
        verify(orderClient).updateOrderStatus(ORDER_NO, 2);
    }

    @Test
    @DisplayName("handleAlipayCallback — 验签失败抛 ILLEGAL_REQUEST 且不置成功")
    void shouldThrow_whenAlipaySignatureInvalid() {
        when(channelRouter.verifyAlipayCallback(anyMap())).thenReturn(false);

        BizException ex = assertThrows(BizException.class,
                () -> callbackManager.handleAlipayCallback(Map.of("out_trade_no", CHANNEL_TRADE_NO)));

        assertEquals(ErrorCode.ILLEGAL_REQUEST, ex.getErrorCode());
        verify(payOrderService, never()).markSuccessIfPending(anyString(), any());
    }


    // ==================== 退款回调（PAY6，D 批从 RefundManagerTest 迁入） ====================

    @Test
    @DisplayName("微信退款回调成功 — 按对外退款项号查单 → 条件置成功 + 回填渠道退款单号 + I42 回写售后单")
    void shouldConfirmRefunded_whenWechatRefundCallbackSuccess() {
        when(channelRouter.parseWechatRefundCallback(any(), anyString()))
                .thenReturn(new WechatRefundCallbackResult(OUT_REFUND_NO, true, "5000099", "SUCCESS"));
        when(refundOrderService.getByOutRefundNo(OUT_REFUND_NO)).thenReturn(refundOrder(0, "WECHAT"));
        when(refundOrderService.markSuccessIfProcessing(REFUND_NO, "5000099")).thenReturn(true);

        callbackManager.handleWechatRefundCallback(Map.of("Wechatpay-Signature", "sig"), "{\"resource\":{}}");

        verify(refundOrderService).getByOutRefundNo(OUT_REFUND_NO);
        verify(refundOrderService, never()).getByRefundNo(OUT_REFUND_NO);
        verify(refundOrderService).markSuccessIfProcessing(REFUND_NO, "5000099");
        verify(afterSaleClient).markRefunded(AFTER_SALE_ID);
    }

    @Test
    @DisplayName("退款成功触达 — 回调置成功：买家收到 REFUND_SUCCESS 站内信（订单号+退款金额+原路渠道）")
    void shouldNotifyBuyerRefundSuccess_whenWechatCallbackConfirmed() {
        when(channelRouter.parseWechatRefundCallback(any(), anyString()))
                .thenReturn(new WechatRefundCallbackResult(OUT_REFUND_NO, true, "5000099", "SUCCESS"));
        when(refundOrderService.getByOutRefundNo(OUT_REFUND_NO)).thenReturn(refundOrder(0, "WECHAT"));
        when(refundOrderService.markSuccessIfProcessing(REFUND_NO, "5000099")).thenReturn(true);

        callbackManager.handleWechatRefundCallback(Map.of(), "{}");

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
    @DisplayName("收件人缺失 — 退款单无 userId：不发买家站内信，I42 回写照常")
    void shouldSkipBuyerMessage_whenRecipientMissing() {
        RefundOrder noBuyer = refundOrder(0, "WECHAT");
        noBuyer.setUserId(null);
        when(channelRouter.parseWechatRefundCallback(any(), anyString()))
                .thenReturn(new WechatRefundCallbackResult(OUT_REFUND_NO, true, "5000099", "SUCCESS"));
        when(refundOrderService.getByOutRefundNo(OUT_REFUND_NO)).thenReturn(noBuyer);
        when(refundOrderService.markSuccessIfProcessing(REFUND_NO, "5000099")).thenReturn(true);

        callbackManager.handleWechatRefundCallback(Map.of(), "{}");

        verifyNoInteractions(notificationClient);
        verify(afterSaleClient).markRefunded(AFTER_SALE_ID);
    }

    @Test
    @DisplayName("微信退款回调非成功终态（CLOSED）— 不改退款单状态、不回写售后单")
    void shouldIgnore_whenWechatRefundCallbackNotSuccess() {
        when(channelRouter.parseWechatRefundCallback(any(), anyString()))
                .thenReturn(new WechatRefundCallbackResult(OUT_REFUND_NO, false, "5000099", "CLOSED"));

        callbackManager.handleWechatRefundCallback(Map.of(), "{}");

        verifyNoInteractions(refundOrderService);
        verifyNoInteractions(afterSaleClient);
    }

    @Test
    @DisplayName("微信退款回调重复通知 — 退款单非处理中：幂等忽略，不重复回写售后单")
    void shouldBeIdempotent_whenDuplicateRefundCallback() {
        when(channelRouter.parseWechatRefundCallback(any(), anyString()))
                .thenReturn(new WechatRefundCallbackResult(OUT_REFUND_NO, true, "5000099", "SUCCESS"));
        when(refundOrderService.getByOutRefundNo(OUT_REFUND_NO)).thenReturn(refundOrder(1, "WECHAT"));
        when(refundOrderService.markSuccessIfProcessing(REFUND_NO, "5000099")).thenReturn(false);

        callbackManager.handleWechatRefundCallback(Map.of(), "{}");

        verify(afterSaleClient, never()).markRefunded(any());
    }

    @Test
    @DisplayName("微信退款回调 — 对外退款项号查无退款单：抛 NO_RESOURCE_FOUND(1103)（控制器转 500 触发渠道重推）")
    void shouldThrow_whenRefundOrderMissingOnCallback() {
        when(channelRouter.parseWechatRefundCallback(any(), anyString()))
                .thenReturn(new WechatRefundCallbackResult("UNKNOWN-OUT", true, "5000099", "SUCCESS"));
        when(refundOrderService.getByOutRefundNo("UNKNOWN-OUT")).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> callbackManager.handleWechatRefundCallback(Map.of(), "{}"));

        // 【语义变更（批次 R2，改严）】3009 → 1103：实体是「退款单」，码名不得再说「订单不存在」；
        // 两者同为 404，HTTP 承载不变，故调用方行为零变化
        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
        assertNotEquals(ErrorCode.ORDER_NOT_FOUND, ex.getErrorCode(),
                "退款单不存在不得再用 ORDER_NOT_FOUND(3009)「订单不存在」");
        assertEquals(404, ex.getErrorCode().getHttpStatus().value(), "承载必须仍是 404（零行为变化）");
        assertTrue(ex.getDisplayMessage().contains("outRefundNo=UNKNOWN-OUT"),
                "文案须带实体名 + 对外退款项号，便于渠道工单定位: " + ex.getDisplayMessage());
    }
}
