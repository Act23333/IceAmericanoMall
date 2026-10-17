package org.icedamericanomall.pay.app;

import org.icedamericanomall.client.AfterSaleClient;
import org.icedamericanomall.client.BalanceClient;
import org.icedamericanomall.client.NotificationClient;
import org.icedamericanomall.client.TradeInvoiceClient;
import org.icedamericanomall.dto.RefundCreateCO;
import org.icedamericanomall.integration.payment.AlipayPaymentClient;
import org.icedamericanomall.integration.payment.PaymentClient;
import org.icedamericanomall.integration.payment.RefundReason;
import org.icedamericanomall.pay.adapter.assembler.RefundConverterImpl;
import org.icedamericanomall.pay.domain.enums.PayTypeEnum;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.domain.repository.InvoiceLinkCompensationRepository;
import org.icedamericanomall.pay.domain.model.refundorder.RefundOrder;
import org.icedamericanomall.pay.support.InMemoryPayOrderRepository;
import org.icedamericanomall.pay.support.InMemoryRefundOrderRepository;
import org.icedamericanomall.pay.support.RecordingPaymentClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 退款原因（渠道 {@code reason}）外发守卫 —— 站在<b>渠道接缝</b>上断言「我方到底把什么文本发给了渠道」
 * （ADR-063 §决策 7 表 #5 / §守卫，批次 B7b）。
 *
 * <p><b>为什么必须站在接缝上</b>：{@code reason} 是人读摘要，会随渠道账单与工单外泄。单号一旦进去，
 * 被渠道截断后就成「半截单号」，客服拿它去查必然查不到 → 升级工单 → 二次外泄。
 * 这里用 {@link RecordingPaymentClient} 记录<b>真实外发值</b>，而不是断言「我们以为发了什么」。
 *
 * <p>断言三条：① 常规摘要原样外发；② 摘要含单号（内部业务键 / 对外渠道号）→ 回落通用文案且不含该号；
 * ③ 超长摘要按 code point 截断到 {@link PaymentClient#MAX_REFUND_REASON_LENGTH}。
 */
@DisplayName("退款原因外发守卫 —— 不得把任何单号塞进渠道 reason")
class RefundReasonWireGuardTest {

    private static final String ORDER_NO = "ORD-REASON-1";
    private static final Long USER_ID = 100L;
    private static final Long AFTER_SALE_ID = 21L;
    private static final int REFUND_AMOUNT = 5000;

    private RecordingPaymentClient wechatClient;
    private RefundManager refundManager;
    private InMemoryRefundOrderRepository refundOrderRepository;
    private PayOrder paidPayOrder;

    @BeforeEach
    void setUp() {
        InMemoryPayOrderRepository payOrderRepository = new InMemoryPayOrderRepository();
        refundOrderRepository = new InMemoryRefundOrderRepository();
        wechatClient = new RecordingPaymentClient();
        PaymentChannelRouter router = new PaymentChannelRouter(wechatClient,
                mock(AlipayPaymentClient.class), mock(BalanceClient.class));
        PayEventNotifier notifier = new PayEventNotifier(mock(NotificationClient.class),
                mock(AfterSaleClient.class), mock(TradeInvoiceClient.class), payOrderRepository,
                mock(InvoiceLinkCompensationRepository.class));
        refundManager = new RefundManager(refundOrderRepository, payOrderRepository, router,
                mock(BalanceClient.class), new RefundConverterImpl(), notifier);

        paidPayOrder = payOrderRepository.createPending(ORDER_NO, USER_ID, REFUND_AMOUNT,
                "WECHAT", PayTypeEnum.NATIVE.getCode());
        payOrderRepository.markSuccessIfPending(paidPayOrder.getPayOrderNo(), "SUCCESS");
    }

    private void refundWithReason(String reason) {
        RefundCreateCO req = new RefundCreateCO();
        req.setAfterSaleId(AFTER_SALE_ID);
        req.setOrderNo(ORDER_NO);
        req.setRefundAmount(REFUND_AMOUNT);
        req.setReason(reason);
        refundManager.refund(req);
    }

    private RefundOrder refundOrder() {
        RefundOrder refundOrder = refundOrderRepository.getByAfterSaleId(AFTER_SALE_ID);
        assertNotNull(refundOrder);
        return refundOrder;
    }

    @Test
    @DisplayName("常规摘要 — 原样外发给渠道（未被无谓改写）")
    void shouldPassThroughPlainReason() {
        refundWithReason("质量问题");

        assertEquals("质量问题", wechatClient.lastRefundReason);
    }

    @Test
    @DisplayName("含内部业务键（pay_order_no 32-hex）→ 回落通用文案，外发文本不含该号")
    void shouldStripInternalBusinessKey() {
        refundWithReason("退款 " + paidPayOrder.getPayOrderNo());

        String sent = wechatClient.lastRefundReason;
        assertNotNull(sent);
        assertEquals(RefundReason.GENERIC.text(), sent);
        assertFalse(sent.contains(paidPayOrder.getPayOrderNo()), "内部单号不得外送: " + sent);
        assertEquals(32, paidPayOrder.getPayOrderNo().length(), "自证：夹具里的内部号确实是 32-hex 业务键");
    }

    @Test
    @DisplayName("含对外退款项号（out_refund_no）→ 回落通用文案，外发文本不含该号")
    void shouldStripOutRefundNo() {
        refundWithReason("质量问题");
        RefundOrder refundOrder = refundOrder();

        // 重放同一退款单（悬挂补偿/重试语义），摘要里塞入本次真实的对外退款项号
        refundManager.execute(refundOrder, paidPayOrder, "重试 " + refundOrder.getOutRefundNo());

        String sent = wechatClient.lastRefundReason;
        assertEquals(RefundReason.GENERIC.text(), sent);
        assertFalse(sent.contains(refundOrder.getOutRefundNo()), "对外退款项号不得进人读摘要: " + sent);
    }

    @Test
    @DisplayName("超长摘要 → 按 code point 截断到 80（渠道上限），不劈字符")
    void shouldTruncateToChannelLimit() {
        refundWithReason("退".repeat(200));

        String sent = wechatClient.lastRefundReason;
        assertNotNull(sent);
        assertEquals(PaymentClient.MAX_REFUND_REASON_LENGTH, sent.length());
        assertTrue(sent.chars().allMatch(c -> c == '退'));
    }

    @Test
    @DisplayName("空摘要 → 外发 null（渠道 reason 可空，不编造文案）")
    void shouldSendNullWhenBlank() {
        refundWithReason("   ");

        assertNull(wechatClient.lastRefundReason);
    }
}
