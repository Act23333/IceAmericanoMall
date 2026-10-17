package org.icedamericanomall.pay.app;

import org.icedamericanomall.client.AfterSaleClient;
import org.icedamericanomall.client.NotificationClient;
import org.icedamericanomall.client.OrderClient;
import org.icedamericanomall.client.TradeInvoiceClient;
import org.icedamericanomall.dto.InAppMessageSendReqCO;
import org.icedamericanomall.pay.adapter.assembler.PayOrderConverter;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.adapter.response.JsapiPayParamsVO;
import org.icedamericanomall.pay.adapter.response.PayOrderVO;
import org.icedamericanomall.dto.OrderSummaryCO;
import org.icedamericanomall.integration.payment.JsapiPayResult;
import org.icedamericanomall.integration.payment.WechatCallbackResult;
import org.icedamericanomall.pay.domain.enums.PayChannelEnum;
import org.icedamericanomall.pay.domain.enums.PayStatusEnum;
import org.icedamericanomall.pay.domain.enums.PayTypeEnum;
import org.icedamericanomall.pay.domain.repository.PayOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PayManager 支付编排单元测试 —— 微信/支付宝/余额三渠道；Feign / 支付渠道 / 领域服务全部打桩。
 * 重点覆盖：扣款金额口径（payAmount）、<b>对外渠道号</b>寻址与落库时序、回调幂等（受影响行数分支）、
 * 超时分支、余额补偿、支付成功买家触达（ADR-042，经真实 PayEventNotifier 打通到 NotificationClient）。
 *
 * <p><b>本类刻意区分三个号</b>：{@code ORD-1}（业务订单号）/ {@code PAY-1}（内部支付单号）/
 * {@code ctn-…}（对外渠道号）。对渠道的调用只允许出现最后一个，回调也只按它寻址 ——
 * 若实现回退成「发起传 order_no、回调查 pay_order_no」，本类的断言会立刻失败。
 *
 * <p>跨「发起 → 落库 → 回调」的<b>真实语义链路</b>（真实聚合工厂 + 内存仓储 + 真实渠道路由）
 * 见 {@link PayChannelTradeNoFlowTest}。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PayManager 支付编排单元测试")
class PayManagerTest {

    /** 业务订单号（外部交易主体，绝不作为渠道号外发）。 */
    private static final String ORDER_NO = "ORD-1";
    /** 内部支付单号（30 位示意，绝不作为渠道号外发）。 */
    private static final String PAY_ORDER_NO = "PAY-1";
    /** 对外渠道号（渠道 out_trade_no 的唯一合法来源）。 */
    private static final String CHANNEL_TRADE_NO = "ctn0000000000000001";

    @Mock PayOrderRepository payOrderService;
    @Mock PayOrderConverter payOrderConverter;
    @Mock OrderClient orderClient;
    @Mock PaymentChannelRouter channelRouter;
    @Mock NotificationClient notificationClient;
    @Mock AfterSaleClient afterSaleClient;
    @Mock TradeInvoiceClient tradeInvoiceClient;

    private PayManager payManager;

    /** 原价 12900，应付 11900（已抵扣 1000 优惠券）。 */
    private OrderSummaryCO summary(int totalAmount, int payAmount) {
        OrderSummaryCO s = new OrderSummaryCO();
        s.setOrderNo(ORDER_NO); s.setUserId(100L);
        s.setTotalAmount(totalAmount); s.setPayAmount(payAmount); s.setStatus(1);
        return s;
    }

    /** 上游摘要金额可空（用于构造「返回 200 但缺字段」的契约违约报文）。 */
    private OrderSummaryCO summaryOf(Integer totalAmount, Integer payAmount) {
        OrderSummaryCO s = new OrderSummaryCO();
        s.setOrderNo(ORDER_NO); s.setUserId(100L);
        s.setTotalAmount(totalAmount); s.setPayAmount(payAmount); s.setStatus(1);
        return s;
    }

    private PayOrder payOrder(int status) {
        PayOrder p = new PayOrder();
        p.setId(1L); p.setBizOrderNo(ORDER_NO); p.setPayOrderNo(PAY_ORDER_NO);
        p.setChannelTradeNo(CHANNEL_TRADE_NO); p.setStatus(status);
        return p;
    }

    @BeforeEach
    void setUp() {
        // 真实触达协作器（ADR-042）：锁死「条件置成功 → 买家触达」全链路到 NotificationClient
        payManager = new PayManager(payOrderService, payOrderConverter, orderClient, channelRouter,
                new PayEventNotifier(notificationClient, afterSaleClient, tradeInvoiceClient, payOrderService));
    }

    @BeforeEach
    void stubConverter() {
        lenient().when(payOrderConverter.toVO(any())).thenReturn(new PayOrderVO());
    }

    // ==================== 发起支付：先落库（渠道号定稿）→ 再调渠道 → 回填链接 ====================

    @Test
    @DisplayName("initiatePayment(WECHAT) — 取应付金额、先落待支付单、用对外渠道号下单、回填链接")
    void shouldInitiateWechat_whenOrderValid() {
        PayOrder created = payOrder(PayStatusEnum.PENDING_PAY.getCode());
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(null);
        when(orderClient.getOrder(ORDER_NO)).thenReturn(summary(12900, 11900));
        when(payOrderService.createPending(ORDER_NO, 100L, 11900, "WECHAT", PayTypeEnum.NATIVE.getCode()))
                .thenReturn(created);
        when(channelRouter.initiatePayment(eq(CHANNEL_TRADE_NO), eq(11900), anyString(),
                eq(PayChannelEnum.WECHAT))).thenReturn("weixin://qr");

        assertNotNull(payManager.initiatePayment(ORDER_NO, 100L, PayChannelEnum.WECHAT));

        // 扣款/支付链接金额必须用 payAmount，而非原价 totalAmount
        verify(channelRouter).initiatePayment(eq(CHANNEL_TRADE_NO), eq(11900), anyString(),
                eq(PayChannelEnum.WECHAT));
        // 渠道号落到支付单自身，链接随后回填到同一行
        verify(payOrderService).createPending(ORDER_NO, 100L, 11900, "WECHAT", PayTypeEnum.NATIVE.getCode());
        verify(payOrderService).attachPayUrl(created, "weixin://qr");
    }

    @Test
    @DisplayName("initiatePayment(WECHAT) — 摘要缺应付金额但原价可用：降级用 totalAmount 并告警（行为不变）")
    void shouldFallbackToTotalAmount_whenPayAmountMissing() {
        PayOrder created = payOrder(PayStatusEnum.PENDING_PAY.getCode());
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(null);
        when(orderClient.getOrder(ORDER_NO)).thenReturn(summaryOf(12900, null));
        when(payOrderService.createPending(ORDER_NO, 100L, 12900, "WECHAT", PayTypeEnum.NATIVE.getCode()))
                .thenReturn(created);
        when(channelRouter.initiatePayment(eq(CHANNEL_TRADE_NO), eq(12900), anyString(),
                eq(PayChannelEnum.WECHAT))).thenReturn("weixin://qr");

        payManager.initiatePayment(ORDER_NO, 100L, PayChannelEnum.WECHAT);

        // 降级口径不变：只有「应付金额」不可用、原价可用时才降级（批次 R2 只改「两者都不可用」的承载）
        verify(channelRouter).initiatePayment(eq(CHANNEL_TRADE_NO), eq(12900), anyString(),
                eq(PayChannelEnum.WECHAT));
    }

    @Test
    @DisplayName("initiatePayment — 摘要缺应付金额且缺原价：INTERNAL_ERROR(1000)/500/BUG（上游契约违约，不是 400）")
    void shouldReject_whenOrderSummaryLacksAmounts() {
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(null);
        when(orderClient.getOrder(ORDER_NO)).thenReturn(summaryOf(null, null));

        BizException ex = assertThrows(BizException.class,
                () -> payManager.initiatePayment(ORDER_NO, 100L, PayChannelEnum.WECHAT));

        // 【语义变更（批次 R2，改严）】400/PARAM_ERROR → 500/INTERNAL_ERROR：上游返回 200 但缺字段 = 契约违约
        assertEquals(ErrorCode.INTERNAL_ERROR, ex.getErrorCode());
        assertNotEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode(),
                "上游返回 200 但缺字段是契约违约，不是「你的参数错」");
        assertNotEquals(ErrorCode.SYSTEM_BUSY, ex.getErrorCode(),
                "数据不完整不是「服务不可用」（历史缺陷 R-4 的反面）");
        assertEquals(500, ex.getErrorCode().getHttpStatus().value());
        assertEquals(ErrorCategory.BUG, ErrorCategory.of(ex.getErrorCode()));
        assertFalse(ErrorCategory.of(ex.getErrorCode()).isRetryable(), "确定性失败不可重试");
        // 文案可定位：带 orderNo（业务标识、不含 PII）
        assertTrue(ex.getDisplayMessage().contains("orderNo=" + ORDER_NO),
                "文案须可定位: " + ex.getDisplayMessage());
        verify(payOrderService, never()).createPending(anyString(), anyLong(), anyInt(), anyString(), anyInt());
        verifyNoInteractions(channelRouter);
    }

    @Test
    @DisplayName("initiatePayment(WECHAT) — 已有待支付单：复用并更新金额/渠道，不新增行、渠道号不变")
    void shouldReusePendingRow_whenReInitiate() {
        PayOrder pending = payOrder(PayStatusEnum.PENDING_PAY.getCode());
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(pending);
        when(orderClient.getOrder(ORDER_NO)).thenReturn(summary(12900, 11900));
        when(payOrderService.resetPending(pending, 11900, "ALIPAY", PayTypeEnum.NATIVE.getCode()))
                .thenReturn(pending);
        when(channelRouter.initiatePayment(eq(CHANNEL_TRADE_NO), eq(11900), anyString(),
                eq(PayChannelEnum.ALIPAY))).thenReturn("https://alipay/pay");

        payManager.initiatePayment(ORDER_NO, 100L, PayChannelEnum.ALIPAY);

        verify(payOrderService).resetPending(pending, 11900, "ALIPAY", PayTypeEnum.NATIVE.getCode());
        verify(payOrderService, never()).createPending(anyString(), anyLong(), anyInt(),
                anyString(), anyInt());
        // 复用行的对外渠道号保持不变（跨重试稳定）
        verify(channelRouter).initiatePayment(eq(CHANNEL_TRADE_NO), eq(11900), anyString(),
                eq(PayChannelEnum.ALIPAY));
    }

    @Test
    @DisplayName("initiatePayment(WECHAT) — 复用条件更新未命中（0 行）：退回新建支付单")
    void shouldCreateNewRow_whenResetPendingMisses() {
        PayOrder pending = payOrder(PayStatusEnum.PENDING_PAY.getCode());
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(pending);
        when(orderClient.getOrder(ORDER_NO)).thenReturn(summary(8800, 8800));
        when(payOrderService.resetPending(eq(pending), anyInt(), anyString(), anyInt())).thenReturn(null);
        when(payOrderService.createPending(eq(ORDER_NO), eq(100L), anyInt(), anyString(), anyInt()))
                .thenReturn(payOrder(PayStatusEnum.PENDING_PAY.getCode()));
        when(channelRouter.initiatePayment(anyString(), anyInt(), anyString(), any()))
                .thenReturn("weixin://qr");

        payManager.initiatePayment(ORDER_NO, 100L, PayChannelEnum.WECHAT);

        verify(payOrderService).createPending(eq(ORDER_NO), eq(100L), anyInt(), anyString(), anyInt());
    }

    @Test
    @DisplayName("initiatePayment(ALIPAY) — 走支付宝客户端拿链接（入参同为对外渠道号）")
    void shouldInitiateAlipay() {
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(null);
        when(orderClient.getOrder(ORDER_NO)).thenReturn(summary(8800, 8800));
        when(payOrderService.createPending(ORDER_NO, 100L, 8800, "ALIPAY", PayTypeEnum.NATIVE.getCode()))
                .thenReturn(payOrder(PayStatusEnum.PENDING_PAY.getCode()));
        when(channelRouter.initiatePayment(eq(CHANNEL_TRADE_NO), eq(8800), anyString(),
                eq(PayChannelEnum.ALIPAY))).thenReturn("https://alipay/pay");

        assertNotNull(payManager.initiatePayment(ORDER_NO, 100L, PayChannelEnum.ALIPAY));

        verify(channelRouter).initiatePayment(eq(CHANNEL_TRADE_NO), eq(8800), anyString(),
                eq(PayChannelEnum.ALIPAY));
    }

    @Test
    @DisplayName("initiatePayment — 已支付订单拒绝重复支付")
    void shouldReject_whenAlreadyPaid() {
        when(payOrderService.getByBizOrderNo(ORDER_NO))
                .thenReturn(payOrder(PayStatusEnum.SUCCESS.getCode()));
        assertThrows(BizException.class, () -> payManager.initiatePayment(ORDER_NO, 100L, PayChannelEnum.WECHAT));
        verify(orderClient, never()).getOrder(anyString());
    }

    // ==================== 余额通道（事务外扣款 + 补偿） ====================

    @Test
    @DisplayName("initiatePayment(BALANCE) — 先落待支付单（无对外渠道号），扣款成功后条件置成功并通知待发货")
    void shouldPayByBalance() {
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(null);
        when(orderClient.getOrder(ORDER_NO)).thenReturn(summary(5000, 5000));
        when(payOrderService.createPending(ORDER_NO, 100L, 5000, "BALANCE",
                PayTypeEnum.BALANCE.getCode())).thenReturn(payOrder(PayStatusEnum.PENDING_PAY.getCode()));
        when(payOrderService.markBalancePaidIfPending(1L)).thenReturn(true);

        payManager.initiatePayment(ORDER_NO, 100L, PayChannelEnum.BALANCE);

        verify(channelRouter).deductBalance(100L, 5000);
        verify(payOrderService).markBalancePaidIfPending(1L);
        verify(orderClient).updateOrderStatus(ORDER_NO, 2);   // 待发货
        verify(payOrderService, never()).cancelPending(anyLong());
        verify(channelRouter, never()).initiatePayment(any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("initiatePayment(BALANCE) — 余额不足：条件取消支付单 + 抛 BALANCE_INSUFFICIENT + 不通知订单")
    void shouldCancelPayOrderAndThrow_whenBalanceInsufficient() {
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(null);
        when(orderClient.getOrder(ORDER_NO)).thenReturn(summary(5000, 5000));
        when(payOrderService.createPending(anyString(), anyLong(), anyInt(), anyString(), anyInt()))
                .thenReturn(payOrder(PayStatusEnum.PENDING_PAY.getCode()));
        doThrow(new BizException(ErrorCode.BALANCE_INSUFFICIENT, "余额不足"))
                .when(channelRouter).deductBalance(100L, 5000);

        BizException ex = assertThrows(BizException.class,
                () -> payManager.initiatePayment(ORDER_NO, 100L, PayChannelEnum.BALANCE));

        assertEquals(ErrorCode.BALANCE_INSUFFICIENT, ex.getErrorCode());
        verify(payOrderService).cancelPending(1L);           // 补偿：条件取消本地支付单
        verify(payOrderService, never()).markBalancePaidIfPending(anyLong());
        verify(orderClient, never()).updateOrderStatus(anyString(), anyInt());
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

        payManager.handleWechatCallback(Map.of("Wechatpay-Signature", "sig"), "{\"id\":\"EV-1\"}");

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

        payManager.handleWechatCallback(Map.of(), "body");

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

        assertDoesNotThrow(() -> payManager.handleWechatCallback(Map.of(), "body"));

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

        payManager.handleWechatCallback(Map.of(), "body");

        verify(orderClient, never()).updateOrderStatus(anyString(), anyInt());
    }

    @Test
    @DisplayName("handleWechatCallback — 非成功终态（CLOSED）：不改支付单状态、不通知订单")
    void shouldNotChangeState_whenTradeStateNotSuccess() {
        when(channelRouter.parseWechatCallback(anyMap(), anyString()))
                .thenReturn(wechatResult(CHANNEL_TRADE_NO, false, "CLOSED"));

        payManager.handleWechatCallback(Map.of(), "body");

        verify(payOrderService, never()).markSuccessIfPending(anyString(), any());
        verify(payOrderService, never()).getByChannelTradeNo(anyString());
        verify(orderClient, never()).updateOrderStatus(anyString(), anyInt());
    }

    @Test
    @DisplayName("handleWechatCallback — 渠道号查不到支付单：抛 ORDER_NOT_FOUND 触发渠道重推")
    void shouldThrowOrderNotFound_whenPayOrderMissing() {
        when(channelRouter.parseWechatCallback(anyMap(), anyString()))
                .thenReturn(wechatResult("ctn-not-exist", true, "SUCCESS"));
        when(payOrderService.getByChannelTradeNo("ctn-not-exist")).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> payManager.handleWechatCallback(Map.of(), "body"));

        assertEquals(ErrorCode.ORDER_NOT_FOUND, ex.getErrorCode());
        verify(orderClient, never()).updateOrderStatus(anyString(), anyInt());
    }

    @Test
    @DisplayName("handleWechatCallback — 验签/解密失败：抛 ILLEGAL_REQUEST 且不置成功")
    void shouldThrow_whenSignatureInvalid() {
        when(channelRouter.parseWechatCallback(anyMap(), anyString()))
                .thenThrow(new BizException(ErrorCode.ILLEGAL_REQUEST, "微信回调验签失败"));

        BizException ex = assertThrows(BizException.class,
                () -> payManager.handleWechatCallback(Map.of(), "body"));

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

        payManager.handleAlipayCallback(Map.of("out_trade_no", CHANNEL_TRADE_NO,
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
                () -> payManager.handleAlipayCallback(Map.of("out_trade_no", CHANNEL_TRADE_NO)));

        assertEquals(ErrorCode.ILLEGAL_REQUEST, ex.getErrorCode());
        verify(payOrderService, never()).markSuccessIfPending(anyString(), any());
    }

    // ==================== PAY7 小程序拉起（微信 JSAPI） ====================

    /** 渠道 JSAPI 下单返回的带签名拉起参数集（六字段，与 SDK 响应同构）。 */
    private JsapiPayResult jsapiResult() {
        return new JsapiPayResult("wx-app", "1760000000", "nonce-str",
                "prepay_id=wx-1", "RSA", "pay-sign");
    }

    @Test
    @DisplayName("initiateJsapiPayment — 取应付金额、先落小程序待支付单、用对外渠道号 JSAPI 下单")
    void shouldInitiateJsapi_whenOrderValid() {
        PayOrder created = payOrder(PayStatusEnum.PENDING_PAY.getCode());
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(null);
        when(orderClient.getOrder(ORDER_NO)).thenReturn(summary(12900, 11900));
        when(payOrderService.createPending(ORDER_NO, 100L, 11900, "WECHAT", PayTypeEnum.MINI.getCode()))
                .thenReturn(created);
        when(channelRouter.initiateWechatJsapi(eq(CHANNEL_TRADE_NO), eq(11900), anyString(), eq("openid-1")))
                .thenReturn(jsapiResult());
        JsapiPayParamsVO vo = new JsapiPayParamsVO();
        when(payOrderConverter.toParamsVO(any())).thenReturn(vo);

        JsapiPayParamsVO result = payManager.initiateJsapiPayment(ORDER_NO, 100L, "openid-1");

        assertSame(vo, result);
        // 金额用 payAmount 而非原价；openid 原样透传渠道做同源绑定；out_trade_no 用渠道号
        verify(channelRouter).initiateWechatJsapi(eq(CHANNEL_TRADE_NO), eq(11900), anyString(), eq("openid-1"));
        verify(payOrderService).createPending(ORDER_NO, 100L, 11900, "WECHAT", PayTypeEnum.MINI.getCode());
    }

    @Test
    @DisplayName("initiateJsapiPayment — 已有待支付单（Native 发起过）：条件重置为小程序方式，不新增行")
    void shouldReusePendingRow_whenJsapiReInitiate() {
        PayOrder pending = payOrder(PayStatusEnum.PENDING_PAY.getCode());
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(pending);
        when(orderClient.getOrder(ORDER_NO)).thenReturn(summary(12900, 11900));
        when(payOrderService.resetPending(pending, 11900, "WECHAT", PayTypeEnum.MINI.getCode()))
                .thenReturn(pending);
        when(channelRouter.initiateWechatJsapi(anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(jsapiResult());
        when(payOrderConverter.toParamsVO(any())).thenReturn(new JsapiPayParamsVO());

        payManager.initiateJsapiPayment(ORDER_NO, 100L, "openid-1");

        verify(payOrderService).resetPending(pending, 11900, "WECHAT", PayTypeEnum.MINI.getCode());
        verify(payOrderService, never()).createPending(anyString(), anyLong(), anyInt(),
                anyString(), anyInt());
        verify(channelRouter).initiateWechatJsapi(eq(CHANNEL_TRADE_NO), eq(11900), anyString(),
                eq("openid-1"));
    }

    @Test
    @DisplayName("initiateJsapiPayment — 幂等：已支付订单拒绝重复拉起且不重复下单")
    void shouldRejectJsapi_whenAlreadyPaid() {
        when(payOrderService.getByBizOrderNo(ORDER_NO))
                .thenReturn(payOrder(PayStatusEnum.SUCCESS.getCode()));

        assertThrows(BizException.class, () -> payManager.initiateJsapiPayment(ORDER_NO, 100L, "openid-1"));

        verify(orderClient, never()).getOrder(anyString());
        verify(channelRouter, never()).initiateWechatJsapi(anyString(), anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("initiateJsapiPayment — 订单不存在：抛 ORDER_NOT_FOUND 且不下单")
    void shouldRejectJsapi_whenOrderMissing() {
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(null);
        when(orderClient.getOrder(ORDER_NO)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> payManager.initiateJsapiPayment(ORDER_NO, 100L, "openid-1"));

        assertEquals(ErrorCode.ORDER_NOT_FOUND, ex.getErrorCode());
        verify(channelRouter, never()).initiateWechatJsapi(anyString(), anyInt(), anyString(), anyString());
    }

    // ==================== 超时关单 ====================

    @Test
    @DisplayName("cancelTimeoutPayOrders — 条件取消命中：同步订单为已取消")
    void shouldCancelTimeouts() {
        when(payOrderService.listTimeout(any(LocalDateTime.class)))
                .thenReturn(List.of(payOrder(PayStatusEnum.PENDING_PAY.getCode())));
        when(payOrderService.cancelPending(1L)).thenReturn(true);

        payManager.cancelTimeoutPayOrders();

        verify(orderClient).updateOrderStatus(ORDER_NO, 5);
    }

    @Test
    @DisplayName("cancelTimeoutPayOrders — 已支付（0 行）：跳过订单取消")
    void shouldSkipOrderCancel_whenPayOrderAlreadyPaid() {
        when(payOrderService.listTimeout(any(LocalDateTime.class)))
                .thenReturn(List.of(payOrder(PayStatusEnum.PENDING_PAY.getCode())));
        when(payOrderService.cancelPending(1L)).thenReturn(false);

        payManager.cancelTimeoutPayOrders();

        verify(orderClient, never()).updateOrderStatus(anyString(), anyInt());
    }
}
