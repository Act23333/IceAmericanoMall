package org.icedamericanomall.pay.app;

import org.icedamericanomall.client.AfterSaleClient;
import org.icedamericanomall.client.BalanceClient;
import org.icedamericanomall.client.NotificationClient;
import org.icedamericanomall.client.TradeInvoiceClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.RefundCreateCO;
import org.icedamericanomall.integration.payment.AlipayPaymentClient;
import org.icedamericanomall.pay.adapter.assembler.RefundConverterImpl;
import org.icedamericanomall.pay.domain.enums.PayTypeEnum;
import org.icedamericanomall.pay.domain.enums.RefundStatusEnum;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.domain.repository.InvoiceLinkCompensationRepository;
import org.icedamericanomall.pay.domain.model.refundorder.RefundOrder;
import org.icedamericanomall.pay.support.InMemoryPayOrderRepository;
import org.icedamericanomall.pay.support.InMemoryRefundOrderRepository;
import org.icedamericanomall.pay.support.RecordingPaymentClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 资金链路「对外退款项号」全链路用例 —— 退款侧的 {@code out_refund_no} 与内部 {@code refund_no}
 * 必须是两个号，且渠道只认前者。
 *
 * <h2>本类要锁死的事实</h2>
 * <ol>
 *   <li>退款发起时 {@code out_refund_no} 由生产代码的聚合工厂生成，与内部 {@code refund_no} 不同，
 *       长度 ≤ 64（微信 {@code out_refund_no} / 支付宝 {@code out_request_no} 上限）；</li>
 *   <li>发给渠道的两个号分别是：原支付的 {@code channel_trade_no}（out_trade_no）与本次的
 *       {@code out_refund_no}（out_refund_no）—— 都不许出现内部键；</li>
 *   <li>退款回调携带 {@code out_refund_no} → 查回退款单并置成功（回填渠道 {@code refund_id}）；</li>
 *   <li>退款回调携带内部 {@code refund_no} → 查不到（两键已分离）。</li>
 * </ol>
 */
@DisplayName("资金链路 — 对外退款项号（out_refund_no）与内部 refund_no 彻底分离")
class RefundChannelNoFlowTest {

    /** 微信 out_refund_no 协议上限（位数）。 */
    private static final int MAX_OUT_REFUND_NO_LENGTH = 64;
    private static final String ORDER_NO = "ORD-FLOW-2";
    private static final Long USER_ID = 100L;
    private static final Long AFTER_SALE_ID = 9L;
    private static final int REFUND_AMOUNT = 12800;

    private InMemoryPayOrderRepository payOrderRepository;
    private InMemoryRefundOrderRepository refundOrderRepository;
    private RecordingPaymentClient wechatClient;
    private RefundManager refundManager;
    private PayOrder paidPayOrder;

    @BeforeEach
    void setUp() {
        payOrderRepository = new InMemoryPayOrderRepository();
        refundOrderRepository = new InMemoryRefundOrderRepository();
        wechatClient = new RecordingPaymentClient();
        NotificationClient notificationClient = mock(NotificationClient.class);
        AfterSaleClient afterSaleClient = mock(AfterSaleClient.class);
        TradeInvoiceClient tradeInvoiceClient = mock(TradeInvoiceClient.class);
        PaymentChannelRouter router = new PaymentChannelRouter(wechatClient,
                mock(AlipayPaymentClient.class), mock(BalanceClient.class));

        PayEventNotifier notifier = new PayEventNotifier(notificationClient, afterSaleClient,
                tradeInvoiceClient, payOrderRepository, mock(InvoiceLinkCompensationRepository.class));
        refundManager = new RefundManager(refundOrderRepository, payOrderRepository, router,
                mock(BalanceClient.class), new RefundConverterImpl(), notifier);

        // 造一笔已支付的微信支付单（渠道号由生产工厂生成）
        paidPayOrder = payOrderRepository.createPending(ORDER_NO, USER_ID, REFUND_AMOUNT,
                "WECHAT", PayTypeEnum.NATIVE.getCode());
        payOrderRepository.markSuccessIfPending(paidPayOrder.getPayOrderNo(), "SUCCESS");
    }

    private RefundCreateCO request() {
        RefundCreateCO req = new RefundCreateCO();
        req.setAfterSaleId(AFTER_SALE_ID);
        req.setOrderNo(ORDER_NO);
        req.setRefundAmount(REFUND_AMOUNT);
        req.setReason("质量问题");
        return req;
    }

    @Test
    @DisplayName("退款发起 — out_refund_no 独立于内部 refund_no 且 ≤ 64，渠道收到的两个号都是对外号")
    void shouldUseExternalIdentifiers_whenRefundRequested() {
        refundManager.refund(request());

        RefundOrder refundOrder = refundOrderRepository.getByAfterSaleId(AFTER_SALE_ID);
        assertNotNull(refundOrder);
        assertNotNull(refundOrder.getOutRefundNo(), "退款单必须落对外退款项号");
        assertNotEquals(refundOrder.getRefundNo(), refundOrder.getOutRefundNo(),
                "对外退款项号不得等于内部退款单号");
        assertTrue(refundOrder.getOutRefundNo().length() >= 12,
                "对外退款项号长度须在 12–20 位口径内: " + refundOrder.getOutRefundNo());
        assertTrue(refundOrder.getOutRefundNo().length() <= MAX_OUT_REFUND_NO_LENGTH);
        assertEquals(32, refundOrder.getRefundNo().length(), "内部退款单号仍是 32 位 hex 业务键");

        // 渠道视角：out_trade_no = 原支付渠道号（不是 pay_order_no），out_refund_no = 对外退款项号
        assertEquals(paidPayOrder.getChannelTradeNo(), wechatClient.lastRefundOutTradeNo);
        assertNotEquals(paidPayOrder.getPayOrderNo(), wechatClient.lastRefundOutTradeNo);
        assertEquals(refundOrder.getOutRefundNo(), wechatClient.lastRefundOutRefundNo);
        // 渠道「受理即处理中」→ 本地保持处理中，等回调
        assertEquals(RefundStatusEnum.PROCESSING.getCode(),
                refundOrderRepository.getByRefundNo(refundOrder.getRefundNo()).getStatus());
    }

    @Test
    @DisplayName("退款回调携带 out_refund_no — 查回退款单、置成功并回填渠道 refund_id")
    void shouldConfirmRefund_whenCallbackCarriesOutRefundNo() {
        refundManager.refund(request());
        RefundOrder refundOrder = refundOrderRepository.getByAfterSaleId(AFTER_SALE_ID);
        wechatClient.refundCallbackOutRefundNo = refundOrder.getOutRefundNo();
        wechatClient.refundCallbackRefundId = "wx-rfd-9001";

        refundManager.handleWechatRefundCallback(Map.of("Wechatpay-Signature", "sig"), "{\"resource\":{}}");

        RefundOrder after = refundOrderRepository.getByRefundNo(refundOrder.getRefundNo());
        assertEquals(RefundStatusEnum.SUCCESS.getCode(), after.getStatus());
        assertEquals("wx-rfd-9001", after.getChannelRefundNo(), "渠道 refund_id 落 channel_refund_no");
        assertEquals(refundOrder.getOutRefundNo(), after.getOutRefundNo(),
                "对外退款项号不因回调被覆盖（否则后续重试/查询立刻失效）");
    }

    @Test
    @DisplayName("退款回调携带内部 refund_no — 查不到（证明两键已彻底分离）")
    void shouldNotFindRefundOrder_whenCallbackCarriesInternalRefundNo() {
        refundManager.refund(request());
        RefundOrder refundOrder = refundOrderRepository.getByAfterSaleId(AFTER_SALE_ID);
        wechatClient.refundCallbackOutRefundNo = refundOrder.getRefundNo();

        BizException ex = assertThrows(BizException.class,
                () -> refundManager.handleWechatRefundCallback(Map.of(), "{}"));

        // 【语义变更（批次 R2，改严）】实体是退款单：3009 → 1103（同为 404，零新增码）
        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
        assertNotEquals(ErrorCode.ORDER_NOT_FOUND, ex.getErrorCode(),
                "退款单不存在不得再用 ORDER_NOT_FOUND(3009)「订单不存在」");
        assertEquals(RefundStatusEnum.PROCESSING.getCode(),
                refundOrderRepository.getByRefundNo(refundOrder.getRefundNo()).getStatus(),
                "查不到即不得推进状态");
    }

    @Test
    @DisplayName("退款回调查询口径 — 悬挂补偿按 out_refund_no 查渠道，不按内部 refund_no")
    void shouldQueryByOutRefundNo_whenResumeStuckRefund() {
        refundManager.refund(request());
        RefundOrder refundOrder = refundOrderRepository.getByAfterSaleId(AFTER_SALE_ID);
        RefundResumeManager resumeManager = new RefundResumeManager(refundOrderRepository, payOrderRepository,
                new PaymentChannelRouter(wechatClient, mock(AlipayPaymentClient.class), mock(BalanceClient.class)),
                refundManager);

        resumeManager.resumeStuckRefunds();

        assertEquals(refundOrder.getOutRefundNo(), wechatClient.lastQueryOutRefundNo);
        assertNotEquals(refundOrder.getRefundNo(), wechatClient.lastQueryOutRefundNo);
    }
}
