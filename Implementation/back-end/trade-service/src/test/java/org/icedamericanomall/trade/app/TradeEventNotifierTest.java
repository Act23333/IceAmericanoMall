package org.icedamericanomall.trade.app;

import org.icedamericanomall.client.NotificationClient;
import org.icedamericanomall.dto.InAppMessageSendReqCO;
import org.icedamericanomall.trade.domain.enums.AfterSaleStatusEnum;
import org.icedamericanomall.trade.domain.enums.AfterSaleTypeEnum;
import org.icedamericanomall.trade.domain.model.aftersale.AfterSale;
import org.icedamericanomall.trade.domain.model.invoice.Invoice;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TradeEventNotifier 买家触达协作器单元测试（ADR-042）。
 *
 * <p>覆盖：包裹发货 / 售后审核结果两事件的站内信字段口径（type/recipient/content/linkUrl）、
 * 审核结论文案（同意/拒绝 + 备注）、fail-open（NotificationClient 异常不外溢）、收件人缺失跳过。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TradeEventNotifier 买家触达协作器单元测试（ADR-042）")
class TradeEventNotifierTest {

    private static final String ORDER_NO = "ORD-1";
    private static final long BUYER_ID = 100L;

    @Mock private NotificationClient notificationClient;

    private TradeEventNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new TradeEventNotifier(notificationClient);
    }

    private Order order(Long buyerId) {
        Order order = new Order();
        order.setOrderNo(ORDER_NO);
        order.setUserId(buyerId);
        return order;
    }

    private AfterSale afterSale(Long buyerId, int status) {
        AfterSale afterSale = new AfterSale();
        afterSale.setOrderNo(ORDER_NO);
        afterSale.setUserId(buyerId);
        afterSale.setType(AfterSaleTypeEnum.REFUND_ONLY.getCode());
        afterSale.setStatus(status);
        return afterSale;
    }

    @Test
    @DisplayName("包裹发货 — 站内信 type=ORDER_SHIPPED，内容含订单号+包裹号，链接指向订单")
    void shouldSendOrderShippedMessage_whenPackageShipped() {
        notifier.notifyOrderShipped(order(BUYER_ID), 2);

        ArgumentCaptor<InAppMessageSendReqCO> captor = ArgumentCaptor.forClass(InAppMessageSendReqCO.class);
        verify(notificationClient).sendInAppMessage(captor.capture());
        InAppMessageSendReqCO message = captor.getValue();
        assertEquals("ORDER_SHIPPED", message.getType());
        assertEquals(BUYER_ID, message.getRecipientId());
        assertTrue(message.getContent().contains(ORDER_NO), "内容须含订单号: " + message.getContent());
        assertTrue(message.getContent().contains("第 2 个包裹"), "内容须含包裹号: " + message.getContent());
        assertEquals("/orders/" + ORDER_NO, message.getLinkUrl());
    }

    @Test
    @DisplayName("审核同意 — 站内信 type=AFTER_SALE_REVIEWED，内容含结论「同意」+ 审核备注")
    void shouldSendApprovedConclusion_whenAfterSaleApproved() {
        notifier.notifyAfterSaleReviewed(afterSale(BUYER_ID, AfterSaleStatusEnum.APPROVED.getCode()),
                "凭证已核实");

        ArgumentCaptor<InAppMessageSendReqCO> captor = ArgumentCaptor.forClass(InAppMessageSendReqCO.class);
        verify(notificationClient).sendInAppMessage(captor.capture());
        InAppMessageSendReqCO message = captor.getValue();
        assertEquals("AFTER_SALE_REVIEWED", message.getType());
        assertEquals(BUYER_ID, message.getRecipientId());
        assertTrue(message.getContent().contains("同意"), "内容须含审核结论: " + message.getContent());
        assertTrue(message.getContent().contains("凭证已核实"), "内容须含审核备注: " + message.getContent());
        assertEquals("/orders/" + ORDER_NO, message.getLinkUrl());
    }

    @Test
    @DisplayName("审核拒绝 — 内容含结论「拒绝」；备注为空时不拼接备注段")
    void shouldSendRejectedConclusionWithoutRemark_whenRemarkBlank() {
        notifier.notifyAfterSaleReviewed(afterSale(BUYER_ID, AfterSaleStatusEnum.REJECTED.getCode()), "  ");

        ArgumentCaptor<InAppMessageSendReqCO> captor = ArgumentCaptor.forClass(InAppMessageSendReqCO.class);
        verify(notificationClient).sendInAppMessage(captor.capture());
        String content = captor.getValue().getContent();
        assertTrue(content.contains("拒绝"), "内容须含审核结论: " + content);
        assertTrue(!content.contains("审核备注"), "空备注不得拼接备注段: " + content);
    }

    @Test
    @DisplayName("收件人缺失 — 订单/售后单无 userId：跳过不发送")
    void shouldSkip_whenRecipientMissing() {
        notifier.notifyOrderShipped(order(null), 1);
        notifier.notifyAfterSaleReviewed(afterSale(null, AfterSaleStatusEnum.APPROVED.getCode()), "备注");

        verifyNoInteractions(notificationClient);
    }

    @Test
    @DisplayName("fail-open — NotificationClient 抛异常：不外溢")
    void shouldNotPropagate_whenNotificationClientFails() {
        doThrow(new RuntimeException("notification-service down"))
                .when(notificationClient).sendInAppMessage(any(InAppMessageSendReqCO.class));

        assertDoesNotThrow(() -> notifier.notifyOrderShipped(order(BUYER_ID), 1));
        assertDoesNotThrow(() -> notifier.notifyAfterSaleReviewed(
                afterSale(BUYER_ID, AfterSaleStatusEnum.REJECTED.getCode()), "不符"));
    }

    @Test
    @DisplayName("防御 — 聚合为 null：跳过且无任何 Feign 交互")
    void shouldDoNothing_whenAggregateNull() {
        assertDoesNotThrow(() -> notifier.notifyOrderShipped(null, 1));
        assertDoesNotThrow(() -> notifier.notifyAfterSaleReviewed(null, "备注"));

        verifyNoInteractions(notificationClient);
    }

    // ==================== 发票触达（F14，ADR-042 矩阵追加事件） ====================

    private Invoice invoice(Long buyerId) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNo("INV-1");
        invoice.setOrderNo(ORDER_NO);
        invoice.setUserId(buyerId);
        return invoice;
    }

    @Test
    @DisplayName("发票开具 — 站内信 type=INVOICE_ISSUED，内容含发票号+订单号，链接指向订单")
    void shouldSendInvoiceIssuedMessage_whenInvoiceIssued() {
        notifier.notifyInvoiceIssued(invoice(BUYER_ID));

        ArgumentCaptor<InAppMessageSendReqCO> captor = ArgumentCaptor.forClass(InAppMessageSendReqCO.class);
        verify(notificationClient).sendInAppMessage(captor.capture());
        InAppMessageSendReqCO message = captor.getValue();
        assertEquals("INVOICE_ISSUED", message.getType());
        assertEquals(BUYER_ID, message.getRecipientId());
        assertTrue(message.getContent().contains("INV-1"), "内容须含发票号: " + message.getContent());
        assertTrue(message.getContent().contains(ORDER_NO));
        assertEquals("/orders/" + ORDER_NO, message.getLinkUrl());
    }

    @Test
    @DisplayName("发票冲红/作废 — 站内信类型区分，内容含原因（系统原因码/人工原因）")
    void shouldSendRedFlushedAndVoidedMessages_withReason() {
        notifier.notifyInvoiceRedFlushed(invoice(BUYER_ID), "SYSTEM_FULL_REFUND:AS-9");
        notifier.notifyInvoiceVoided(invoice(BUYER_ID), "买家撤回");

        ArgumentCaptor<InAppMessageSendReqCO> captor = ArgumentCaptor.forClass(InAppMessageSendReqCO.class);
        verify(notificationClient, org.mockito.Mockito.times(2)).sendInAppMessage(captor.capture());
        InAppMessageSendReqCO redFlushed = captor.getAllValues().get(0);
        assertEquals("INVOICE_RED_FLUSHED", redFlushed.getType());
        assertTrue(redFlushed.getContent().contains("SYSTEM_FULL_REFUND:AS-9"));
        assertEquals("/orders/" + ORDER_NO, redFlushed.getLinkUrl());
        InAppMessageSendReqCO voided = captor.getAllValues().get(1);
        assertEquals("INVOICE_VOIDED", voided.getType());
        assertTrue(voided.getContent().contains("买家撤回"));
    }

    @Test
    @DisplayName("发票触达 — 发票为 null 或收件人缺失：跳过；client 异常不外溢（fail-open）")
    void shouldSkipAndStayOpen_whenInvoiceNotifyDegraded() {
        assertDoesNotThrow(() -> notifier.notifyInvoiceIssued(null));
        assertDoesNotThrow(() -> notifier.notifyInvoiceIssued(invoice(null)));
        assertDoesNotThrow(() -> notifier.notifyInvoiceRedFlushed(null, "原因"));
        assertDoesNotThrow(() -> notifier.notifyInvoiceVoided(invoice(null), "原因"));
        verifyNoInteractions(notificationClient);

        doThrow(new RuntimeException("notification-service down"))
                .when(notificationClient).sendInAppMessage(any(InAppMessageSendReqCO.class));
        assertDoesNotThrow(() -> notifier.notifyInvoiceIssued(invoice(BUYER_ID)));
    }
}
