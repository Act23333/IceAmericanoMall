package org.icedamericanomall.pay.app;

import org.icedamericanomall.client.AfterSaleClient;
import org.icedamericanomall.client.BalanceClient;
import org.icedamericanomall.client.NotificationClient;
import org.icedamericanomall.client.OrderClient;
import org.icedamericanomall.client.TradeInvoiceClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.OrderSummaryCO;
import org.icedamericanomall.integration.payment.AlipayPaymentClient;
import org.icedamericanomall.integration.payment.PaymentClient;
import org.icedamericanomall.pay.adapter.assembler.PayOrderConverterImpl;
import org.icedamericanomall.pay.domain.enums.PayChannelEnum;
import org.icedamericanomall.pay.domain.enums.PayStatusEnum;
import org.icedamericanomall.pay.domain.enums.PayTypeEnum;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.domain.repository.InvoiceLinkCompensationRepository;
import org.icedamericanomall.pay.support.InMemoryPayOrderRepository;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 资金链路「对外渠道号」全链路用例 —— <b>修复前必然失败</b>的那条路径的真实覆盖。
 *
 * <h2>本类要锁死的事实</h2>
 * <ol>
 *   <li>发起支付时落库的 {@code channel_trade_no} 与内部 {@code order_no} / {@code pay_order_no}
 *       完全不同，且长度 ≤ 渠道协议上限（微信 {@code out_trade_no} 32 位）；</li>
 *   <li>发给渠道的 {@code out_trade_no} 就是落库的那个渠道号（不是 order_no）；</li>
 *   <li>渠道回调携带该渠道号 → 能查回支付单并置成功；</li>
 *   <li>渠道回调携带<b>内部</b> order_no 或 pay_order_no（历史错误用法）→ 查不到
 *       （证明两个键已彻底分离，不再有混用空间）；</li>
 *   <li>同一支付单重复发起 → 渠道号<b>不变</b>（跨重试稳定，渠道侧不会出现两个商户单号）。</li>
 * </ol>
 *
 * <h2>为什么不是「打桩仓储 + 硬编码 out_trade_no」</h2>
 * 历史单测正是以 {@code Map.of("out_trade_no", "PAY-1")} 配桩仓储，让「回调按 pay_order_no 查」
 * 这条错口径看起来通过。本类改用 {@link InMemoryPayOrderRepository}（按列语义真实落库）+
 * {@link RecordingPaymentClient}（记录渠道真正收到的号）+ 真实聚合工厂与真实渠道路由，
 * 任何一侧的键错位都会立刻红。
 */
@DisplayName("资金链路 — 对外渠道号（out_trade_no）与内部业务键彻底分离")
class PayChannelTradeNoFlowTest {

    /** 业务订单号（orders.order_no）。 */
    private static final String ORDER_NO = "ORD-FLOW-1";
    private static final Long USER_ID = 100L;
    private static final int PAY_AMOUNT = 11900;

    private InMemoryPayOrderRepository payOrderRepository;
    private RecordingPaymentClient wechatClient;
    private OrderClient orderClient;
    private PayManager payManager;

    @BeforeEach
    void setUp() {
        payOrderRepository = new InMemoryPayOrderRepository();
        wechatClient = new RecordingPaymentClient();
        orderClient = mock(OrderClient.class);
        NotificationClient notificationClient = mock(NotificationClient.class);
        AfterSaleClient afterSaleClient = mock(AfterSaleClient.class);
        TradeInvoiceClient tradeInvoiceClient = mock(TradeInvoiceClient.class);
        PaymentChannelRouter router = new PaymentChannelRouter(wechatClient,
                mock(AlipayPaymentClient.class), mock(BalanceClient.class));

        payManager = new PayManager(payOrderRepository, new PayOrderConverterImpl(), orderClient, router,
                new PayEventNotifier(notificationClient, afterSaleClient, tradeInvoiceClient,
                        payOrderRepository, mock(InvoiceLinkCompensationRepository.class)));

        OrderSummaryCO summary = new OrderSummaryCO();
        summary.setOrderNo(ORDER_NO);
        summary.setUserId(USER_ID);
        summary.setTotalAmount(PAY_AMOUNT);
        summary.setPayAmount(PAY_AMOUNT);
        summary.setStatus(1);
        when(orderClient.getOrder(ORDER_NO)).thenReturn(summary);
    }

    @Test
    @DisplayName("发起支付 — 渠道号落库且 ≠ order_no / pay_order_no，长度 ≤ 渠道 out_trade_no 上限")
    void shouldPersistChannelTradeNoDistinctFromInternalKeys_whenInitiate() {
        payManager.initiatePayment(ORDER_NO, USER_ID, PayChannelEnum.WECHAT);

        PayOrder persisted = payOrderRepository.getByBizOrderNo(ORDER_NO);
        String channelTradeNo = persisted.getChannelTradeNo();

        assertNotNull(channelTradeNo, "微信渠道必须落对外渠道号");
        assertNotEquals(ORDER_NO, channelTradeNo, "渠道号不得等于业务订单号（历史缺陷即此处）");
        assertNotEquals(persisted.getPayOrderNo(), channelTradeNo, "渠道号不得等于内部支付单号");
        assertTrue(channelTradeNo.length() >= 12,
                "渠道号长度须在 12–20 位口径内: " + channelTradeNo);
        assertTrue(channelTradeNo.length() <= PaymentClient.MAX_OUT_TRADE_NO_LENGTH,
                "渠道号必须留出余量（上限 " + PaymentClient.MAX_OUT_TRADE_NO_LENGTH + "）: " + channelTradeNo);
        assertNotEquals(PaymentClient.MAX_OUT_TRADE_NO_LENGTH, channelTradeNo.length(),
                "不得等于上限本身（零余量：加任何前缀即超限）");
        // 内部支付单号仍是 32 位 hex 业务键，与渠道号的形态本就不同（对比印证「零余量」问题）
        assertEquals(32, persisted.getPayOrderNo().length());
        // 渠道真正收到的 out_trade_no 就是落库的渠道号，且支付链接回填到同一行
        assertEquals(channelTradeNo, wechatClient.lastInitiateOutTradeNo);
        assertNotNull(persisted.getQrCodeUrl(), "渠道下单成功应回填支付链接");
    }

    @Test
    @DisplayName("重复发起同一支付单 — 复用待支付行，渠道号不变（跨重试稳定）")
    void shouldKeepChannelTradeNo_whenInitiateTwice() {
        payManager.initiatePayment(ORDER_NO, USER_ID, PayChannelEnum.WECHAT);
        String firstChannelTradeNo = payOrderRepository.getByBizOrderNo(ORDER_NO).getChannelTradeNo();

        payManager.initiatePayment(ORDER_NO, USER_ID, PayChannelEnum.WECHAT);

        PayOrder persisted = payOrderRepository.getByBizOrderNo(ORDER_NO);
        assertEquals(firstChannelTradeNo, persisted.getChannelTradeNo(), "渠道号跨重试不得重新生成");
        assertEquals(firstChannelTradeNo, wechatClient.lastInitiateOutTradeNo);
        assertEquals(1, payOrderRepository.rowCount(), "复用待支付单，不新增支付行");
    }

    @Test
    @DisplayName("JSAPI 小程序发起 — 发给渠道的 out_trade_no 同样是渠道号（不是 order_no）")
    void shouldUseChannelTradeNo_whenJsapiInitiate() {
        payManager.initiateJsapiPayment(ORDER_NO, USER_ID, "openid-1");

        PayOrder persisted = payOrderRepository.getByBizOrderNo(ORDER_NO);
        assertNotNull(persisted.getChannelTradeNo());
        assertEquals(persisted.getChannelTradeNo(), wechatClient.lastJsapiOutTradeNo);
        assertNotEquals(ORDER_NO, wechatClient.lastJsapiOutTradeNo);
    }

    @Test
    @DisplayName("渠道回调携带渠道号 — 查回支付单并置成功、通知订单待发货（正向链路）")
    void shouldConfirmPaid_whenCallbackCarriesChannelTradeNo() {
        payManager.initiatePayment(ORDER_NO, USER_ID, PayChannelEnum.WECHAT);
        PayOrder persisted = payOrderRepository.getByBizOrderNo(ORDER_NO);
        wechatClient.callbackOutTradeNo = persisted.getChannelTradeNo();

        payManager.handleWechatCallback(Map.of("Wechatpay-Signature", "sig"), "{\"id\":\"EV-1\"}");

        PayOrder afterCallback = payOrderRepository.getByBizOrderNo(ORDER_NO);
        assertEquals(PayStatusEnum.SUCCESS.getCode(), afterCallback.getStatus(), "回调应置支付成功");
        assertEquals("SUCCESS", afterCallback.getResultCode());
        verify(orderClient).updateOrderStatus(ORDER_NO, 2);
    }

    @Test
    @DisplayName("渠道回调携带内部 order_no / pay_order_no — 一律查不到（证明两键已彻底分离）")
    void shouldNotFindPayOrder_whenCallbackCarriesInternalKeys() {
        payManager.initiatePayment(ORDER_NO, USER_ID, PayChannelEnum.WECHAT);
        PayOrder persisted = payOrderRepository.getByBizOrderNo(ORDER_NO);

        // ① 历史错误用法：把 orders.order_no 当 out_trade_no
        wechatClient.callbackOutTradeNo = ORDER_NO;
        BizException byOrderNo = assertThrows(BizException.class,
                () -> payManager.handleWechatCallback(Map.of(), "{}"));
        // U11：查的是支付单，资源不存在统一 NO_RESOURCE_FOUND(1103)/404（不是 3009「订单不存在」）
        assertEquals(ErrorCode.NO_RESOURCE_FOUND, byOrderNo.getErrorCode());

        // ② 另一种历史混用：把 pay_order_no 当 out_trade_no
        wechatClient.callbackOutTradeNo = persisted.getPayOrderNo();
        BizException byPayOrderNo = assertThrows(BizException.class,
                () -> payManager.handleWechatCallback(Map.of(), "{}"));
        assertEquals(ErrorCode.NO_RESOURCE_FOUND, byPayOrderNo.getErrorCode());

        // 两次都不得推进支付单，也不得通知订单
        assertEquals(PayStatusEnum.PENDING_PAY.getCode(),
                payOrderRepository.getByBizOrderNo(ORDER_NO).getStatus());
        verify(orderClient, never()).updateOrderStatus(anyString(), anyInt());
    }

    @Test
    @DisplayName("余额渠道 — 本地闭环不对外发起，渠道号为空（不伪造对外号）")
    void shouldLeaveChannelTradeNoNull_whenBalanceChannel() {
        payManager.initiatePayment(ORDER_NO, USER_ID, PayChannelEnum.BALANCE);

        PayOrder persisted = payOrderRepository.getByBizOrderNo(ORDER_NO);
        assertEquals(PayTypeEnum.BALANCE.getCode(), persisted.getPayType());
        assertEquals(null, persisted.getChannelTradeNo(), "余额渠道不对外发起，不应有渠道号");
        assertEquals(null, wechatClient.lastInitiateOutTradeNo, "余额渠道不得调用渠道客户端");
    }
}
