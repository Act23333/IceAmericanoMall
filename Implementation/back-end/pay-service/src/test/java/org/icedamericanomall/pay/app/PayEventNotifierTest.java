package org.icedamericanomall.pay.app;

import org.icedamericanomall.client.AfterSaleClient;
import org.icedamericanomall.client.NotificationClient;
import org.icedamericanomall.dto.InAppMessageSendReqCO;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.domain.model.refundorder.RefundOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * PayEventNotifier 买家触达协作器单元测试（ADR-042）。
 *
 * <p>覆盖：支付成功 / 退款成功两事件的站内信字段口径（type/recipient/content/linkUrl）、
 * 退款成功「先 I42 回写、后买家触达」顺序、fail-open（Notification/AfterSale 客户端异常不外溢）、
 * 收件人缺失跳过不发送。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PayEventNotifier 买家触达协作器单元测试（ADR-042）")
class PayEventNotifierTest {

    private static final String ORDER_NO = "ORD-1";
    private static final long BUYER_ID = 100L;
    private static final long AFTER_SALE_ID = 9L;
    private static final int AMOUNT = 12800;

    @Mock private NotificationClient notificationClient;
    @Mock private AfterSaleClient afterSaleClient;

    private PayEventNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new PayEventNotifier(notificationClient, afterSaleClient);
    }

    private PayOrder payOrder(Long buyerId, Integer amount) {
        PayOrder payOrder = new PayOrder();
        payOrder.setPayOrderNo("PAY-1");
        payOrder.setBizOrderNo(ORDER_NO);
        payOrder.setBizUserId(buyerId);
        payOrder.setAmount(amount);
        return payOrder;
    }

    private RefundOrder refundOrder(Long buyerId, String channel, Integer amount) {
        RefundOrder refundOrder = new RefundOrder();
        refundOrder.setRefundNo("RFD-1");
        refundOrder.setOrderNo(ORDER_NO);
        refundOrder.setAfterSaleId(AFTER_SALE_ID);
        refundOrder.setUserId(buyerId);
        refundOrder.setChannel(channel);
        refundOrder.setRefundAmount(amount);
        return refundOrder;
    }

    @Test
    @DisplayName("支付成功 — 站内信 type=PAY_SUCCESS，内容含订单号+实付金额，链接指向订单")
    void shouldSendPaySuccessMessage_whenPayOrderConfirmed() {
        notifier.notifyPaySuccess(payOrder(BUYER_ID, 11900));

        ArgumentCaptor<InAppMessageSendReqCO> captor = ArgumentCaptor.forClass(InAppMessageSendReqCO.class);
        verify(notificationClient).sendInAppMessage(captor.capture());
        InAppMessageSendReqCO message = captor.getValue();
        assertEquals("PAY_SUCCESS", message.getType());
        assertEquals(BUYER_ID, message.getRecipientId());
        assertTrue(message.getContent().contains(ORDER_NO), "内容须含订单号: " + message.getContent());
        assertTrue(message.getContent().contains("119.00"), "内容须含实付金额(元): " + message.getContent());
        assertEquals("/orders/" + ORDER_NO, message.getLinkUrl());
        verifyNoInteractions(afterSaleClient);
    }

    @Test
    @DisplayName("退款成功 — 先 I42 回写售后单、后买家站内信（REFUND_SUCCESS，含订单号+金额+原路渠道）")
    void shouldWriteBackBeforeBuyerMessage_whenRefundSuccess() {
        notifier.onRefundSuccess(refundOrder(BUYER_ID, "WECHAT", AMOUNT));

        InOrder inOrder = inOrder(afterSaleClient, notificationClient);
        inOrder.verify(afterSaleClient).markRefunded(AFTER_SALE_ID);
        ArgumentCaptor<InAppMessageSendReqCO> captor = ArgumentCaptor.forClass(InAppMessageSendReqCO.class);
        inOrder.verify(notificationClient).sendInAppMessage(captor.capture());
        InAppMessageSendReqCO message = captor.getValue();
        assertEquals("REFUND_SUCCESS", message.getType());
        assertEquals(BUYER_ID, message.getRecipientId());
        assertTrue(message.getContent().contains(ORDER_NO), "内容须含订单号: " + message.getContent());
        assertTrue(message.getContent().contains("128.00"), "内容须含退款金额(元): " + message.getContent());
        assertTrue(message.getContent().contains("微信支付"), "内容须含原路渠道: " + message.getContent());
        assertEquals("/orders/" + ORDER_NO, message.getLinkUrl());
    }

    @Test
    @DisplayName("收件人缺失 — 支付单无 bizUserId：跳过不发送")
    void shouldSkipPaySuccessMessage_whenRecipientMissing() {
        notifier.notifyPaySuccess(payOrder(null, AMOUNT));

        verifyNoInteractions(notificationClient);
    }

    @Test
    @DisplayName("收件人缺失 — 退款单无 userId：不发买家站内信，I42 回写照常")
    void shouldSkipBuyerMessageButWriteBack_whenRefundRecipientMissing() {
        notifier.onRefundSuccess(refundOrder(null, "WECHAT", AMOUNT));

        verifyNoInteractions(notificationClient);
        verify(afterSaleClient).markRefunded(AFTER_SALE_ID);
    }

    @Test
    @DisplayName("fail-open — NotificationClient 抛异常：不外溢，退款 I42 回写不受影响")
    void shouldNotPropagate_whenNotificationClientFails() {
        doThrow(new RuntimeException("notification-service down"))
                .when(notificationClient).sendInAppMessage(any(InAppMessageSendReqCO.class));

        assertDoesNotThrow(() -> notifier.notifyPaySuccess(payOrder(BUYER_ID, AMOUNT)));
        assertDoesNotThrow(() -> notifier.onRefundSuccess(refundOrder(BUYER_ID, "BALANCE", AMOUNT)));

        verify(afterSaleClient).markRefunded(AFTER_SALE_ID);
    }

    @Test
    @DisplayName("fail-open — I42 回写抛异常：买家触达仍发送")
    void shouldStillNotifyBuyer_whenAfterSaleWriteBackFails() {
        doThrow(new RuntimeException("trade-service down"))
                .when(afterSaleClient).markRefunded(AFTER_SALE_ID);

        assertDoesNotThrow(() -> notifier.onRefundSuccess(refundOrder(BUYER_ID, "WECHAT", AMOUNT)));

        verify(notificationClient).sendInAppMessage(any(InAppMessageSendReqCO.class));
    }

    @Test
    @DisplayName("防御 — 聚合为 null：跳过且无任何 Feign 交互")
    void shouldDoNothing_whenAggregateNull() {
        assertDoesNotThrow(() -> notifier.notifyPaySuccess(null));
        assertDoesNotThrow(() -> notifier.onRefundSuccess(null));

        verifyNoInteractions(notificationClient, afterSaleClient);
        verify(afterSaleClient, never()).markRefunded(any());
    }
}
