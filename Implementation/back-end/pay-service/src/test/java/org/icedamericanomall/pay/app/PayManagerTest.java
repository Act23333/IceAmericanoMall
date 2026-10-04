package org.icedamericanomall.pay.app;

import org.icedamericanomall.client.OrderClient;
import org.icedamericanomall.pay.adapter.assembler.PayOrderConverter;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.adapter.response.PayOrderVO;
import org.icedamericanomall.dto.OrderSummaryCO;
import org.icedamericanomall.integration.payment.WechatCallbackResult;
import org.icedamericanomall.pay.domain.enums.PayChannelEnum;
import org.icedamericanomall.pay.domain.enums.PayStatusEnum;
import org.icedamericanomall.pay.domain.enums.PayTypeEnum;
import org.icedamericanomall.pay.domain.repository.PayOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
 * 重点覆盖：扣款金额口径（payAmount）、回调幂等（受影响行数分支）、超时分支、余额补偿。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PayManager 支付编排单元测试")
class PayManagerTest {

    @Mock PayOrderRepository payOrderService;
    @Mock PayOrderConverter payOrderConverter;
    @Mock OrderClient orderClient;
    @Mock PaymentChannelRouter channelRouter;
    @InjectMocks PayManager payManager;

    /** 原价 12900，应付 11900（已抵扣 1000 优惠券）。 */
    private OrderSummaryCO summary(int totalAmount, int payAmount) {
        OrderSummaryCO s = new OrderSummaryCO();
        s.setOrderNo("ORD-1"); s.setUserId(100L);
        s.setTotalAmount(totalAmount); s.setPayAmount(payAmount); s.setStatus(1);
        return s;
    }

    private PayOrder payOrder(int status) {
        PayOrder p = new PayOrder();
        p.setId(1L); p.setBizOrderNo("ORD-1"); p.setPayOrderNo("PAY-1"); p.setStatus(status);
        return p;
    }

    @BeforeEach
    void stubConverter() {
        lenient().when(payOrderConverter.toVO(any())).thenReturn(new PayOrderVO());
    }

    // ==================== 发起支付 ====================

    @Test
    @DisplayName("initiatePayment(WECHAT) — 取应付金额、生成二维码、创建待支付单")
    void shouldInitiateWechat_whenOrderValid() {
        when(payOrderService.getByBizOrderNo("ORD-1")).thenReturn(null);
        when(orderClient.getOrder("ORD-1")).thenReturn(summary(12900, 11900));
        when(channelRouter.initiatePayment(eq("ORD-1"), eq(11900), anyString(), eq(PayChannelEnum.WECHAT)))
                .thenReturn("weixin://qr");
        when(payOrderService.createPending("ORD-1", 100L, 11900, "weixin://qr", "WECHAT",
                PayTypeEnum.NATIVE.getCode())).thenReturn(payOrder(PayStatusEnum.PENDING_PAY.getCode()));

        assertNotNull(payManager.initiatePayment("ORD-1", 100L, PayChannelEnum.WECHAT));
        // 扣款/支付链接金额必须用 payAmount，而非原价 totalAmount
        verify(channelRouter).initiatePayment(eq("ORD-1"), eq(11900), anyString(), eq(PayChannelEnum.WECHAT));
        verify(payOrderService).createPending("ORD-1", 100L, 11900, "weixin://qr", "WECHAT",
                PayTypeEnum.NATIVE.getCode());
    }

    @Test
    @DisplayName("initiatePayment(WECHAT) — 已有待支付单：复用并更新金额/渠道，不新增行")
    void shouldReusePendingRow_whenReInitiate() {
        PayOrder pending = payOrder(PayStatusEnum.PENDING_PAY.getCode());
        when(payOrderService.getByBizOrderNo("ORD-1")).thenReturn(pending);
        when(orderClient.getOrder("ORD-1")).thenReturn(summary(12900, 11900));
        when(channelRouter.initiatePayment(eq("ORD-1"), eq(11900), anyString(), eq(PayChannelEnum.ALIPAY)))
                .thenReturn("https://alipay/pay");
        when(payOrderService.resetPending(pending, 11900, "ALIPAY", "https://alipay/pay",
                PayTypeEnum.NATIVE.getCode())).thenReturn(pending);

        payManager.initiatePayment("ORD-1", 100L, PayChannelEnum.ALIPAY);

        verify(payOrderService).resetPending(pending, 11900, "ALIPAY", "https://alipay/pay",
                PayTypeEnum.NATIVE.getCode());
        verify(payOrderService, never()).createPending(anyString(), anyLong(), anyInt(),
                any(), anyString(), anyInt());
    }

    @Test
    @DisplayName("initiatePayment(WECHAT) — 复用条件更新未命中（0 行）：退回新建支付单")
    void shouldCreateNewRow_whenResetPendingMisses() {
        PayOrder pending = payOrder(PayStatusEnum.PENDING_PAY.getCode());
        when(payOrderService.getByBizOrderNo("ORD-1")).thenReturn(pending);
        when(orderClient.getOrder("ORD-1")).thenReturn(summary(8800, 8800));
        when(channelRouter.initiatePayment(anyString(), anyInt(), anyString(), any()))
                .thenReturn("weixin://qr");
        when(payOrderService.resetPending(eq(pending), anyInt(), anyString(), anyString(), anyInt()))
                .thenReturn(null);
        when(payOrderService.createPending(eq("ORD-1"), eq(100L), anyInt(), anyString(), anyString(), anyInt()))
                .thenReturn(payOrder(PayStatusEnum.PENDING_PAY.getCode()));

        payManager.initiatePayment("ORD-1", 100L, PayChannelEnum.WECHAT);

        verify(payOrderService).createPending(eq("ORD-1"), eq(100L), anyInt(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("initiatePayment(ALIPAY) — 走支付宝客户端拿链接")
    void shouldInitiateAlipay() {
        when(payOrderService.getByBizOrderNo("ORD-1")).thenReturn(null);
        when(orderClient.getOrder("ORD-1")).thenReturn(summary(8800, 8800));
        when(channelRouter.initiatePayment(eq("ORD-1"), eq(8800), anyString(), eq(PayChannelEnum.ALIPAY)))
                .thenReturn("https://alipay/pay");
        when(payOrderService.createPending("ORD-1", 100L, 8800, "https://alipay/pay", "ALIPAY",
                PayTypeEnum.NATIVE.getCode())).thenReturn(payOrder(PayStatusEnum.PENDING_PAY.getCode()));

        assertNotNull(payManager.initiatePayment("ORD-1", 100L, PayChannelEnum.ALIPAY));
        verify(channelRouter).initiatePayment(eq("ORD-1"), eq(8800), anyString(), eq(PayChannelEnum.ALIPAY));
    }

    @Test
    @DisplayName("initiatePayment — 已支付订单拒绝重复支付")
    void shouldReject_whenAlreadyPaid() {
        when(payOrderService.getByBizOrderNo("ORD-1"))
                .thenReturn(payOrder(PayStatusEnum.SUCCESS.getCode()));
        assertThrows(BizException.class, () -> payManager.initiatePayment("ORD-1", 100L, PayChannelEnum.WECHAT));
        verify(orderClient, never()).getOrder(anyString());
    }

    // ==================== 余额通道（事务外扣款 + 补偿） ====================

    @Test
    @DisplayName("initiatePayment(BALANCE) — 先落待支付单，扣款成功后条件置成功并通知待发货")
    void shouldPayByBalance() {
        when(payOrderService.getByBizOrderNo("ORD-1")).thenReturn(null);
        when(orderClient.getOrder("ORD-1")).thenReturn(summary(5000, 5000));
        when(payOrderService.createPending("ORD-1", 100L, 5000, null, "BALANCE",
                PayTypeEnum.BALANCE.getCode())).thenReturn(payOrder(PayStatusEnum.PENDING_PAY.getCode()));
        when(payOrderService.markBalancePaidIfPending(1L)).thenReturn(true);

        payManager.initiatePayment("ORD-1", 100L, PayChannelEnum.BALANCE);

        verify(channelRouter).deductBalance(100L, 5000);
        verify(payOrderService).markBalancePaidIfPending(1L);
        verify(orderClient).updateOrderStatus("ORD-1", 2);   // 待发货
        verify(payOrderService, never()).cancelPending(anyLong());
    }

    @Test
    @DisplayName("initiatePayment(BALANCE) — 余额不足：条件取消支付单 + 抛 BALANCE_INSUFFICIENT + 不通知订单")
    void shouldCancelPayOrderAndThrow_whenBalanceInsufficient() {
        when(payOrderService.getByBizOrderNo("ORD-1")).thenReturn(null);
        when(orderClient.getOrder("ORD-1")).thenReturn(summary(5000, 5000));
        when(payOrderService.createPending(anyString(), anyLong(), anyInt(), any(), anyString(), anyInt()))
                .thenReturn(payOrder(PayStatusEnum.PENDING_PAY.getCode()));
        doThrow(new BizException(ErrorCode.BALANCE_INSUFFICIENT, "余额不足"))
                .when(channelRouter).deductBalance(100L, 5000);

        BizException ex = assertThrows(BizException.class,
                () -> payManager.initiatePayment("ORD-1", 100L, PayChannelEnum.BALANCE));

        assertEquals(ErrorCode.BALANCE_INSUFFICIENT, ex.getErrorCode());
        verify(payOrderService).cancelPending(1L);           // 补偿：条件取消本地支付单
        verify(payOrderService, never()).markBalancePaidIfPending(anyLong());
        verify(orderClient, never()).updateOrderStatus(anyString(), anyInt());
    }

    // ==================== 回调幂等（微信：通道适配层整包解析；支付宝：表单参数） ====================

    /** 微信回调解析结果：{@code success} 由 {@code trade_state} 判定。 */
    private WechatCallbackResult wechatResult(String payOrderNo, boolean success, String tradeState) {
        return new WechatCallbackResult(payOrderNo, success, "TXN-1", tradeState);
    }

    @Test
    @DisplayName("handleWechatCallback — 解析成功（trade_state=SUCCESS）且条件更新命中：通知订单待发货")
    void shouldMarkSuccess_whenWechatCallbackParsed() {
        when(channelRouter.parseWechatCallback(anyMap(), anyString()))
                .thenReturn(wechatResult("PAY-1", true, "SUCCESS"));
        when(payOrderService.getByPayOrderNo("PAY-1"))
                .thenReturn(payOrder(PayStatusEnum.PENDING_PAY.getCode()));
        when(payOrderService.markSuccessIfPending("PAY-1", "SUCCESS")).thenReturn(true);

        payManager.handleWechatCallback(Map.of("Wechatpay-Signature", "sig"), "{\"id\":\"EV-1\"}");

        verify(payOrderService).markSuccessIfPending("PAY-1", "SUCCESS");
        verify(orderClient).updateOrderStatus("ORD-1", 2);
    }

    @Test
    @DisplayName("handleWechatCallback — 并发重复回调：条件更新 0 行，不再重复通知订单")
    void shouldSkipNotify_whenCallbackAlreadyProcessed() {
        when(channelRouter.parseWechatCallback(anyMap(), anyString()))
                .thenReturn(wechatResult("PAY-1", true, "SUCCESS"));
        when(payOrderService.getByPayOrderNo("PAY-1"))
                .thenReturn(payOrder(PayStatusEnum.SUCCESS.getCode()));
        when(payOrderService.markSuccessIfPending("PAY-1", "SUCCESS")).thenReturn(false);

        payManager.handleWechatCallback(Map.of(), "body");

        verify(orderClient, never()).updateOrderStatus(anyString(), anyInt());
    }

    @Test
    @DisplayName("handleWechatCallback — 非成功终态（CLOSED）：不改支付单状态、不通知订单")
    void shouldNotChangeState_whenTradeStateNotSuccess() {
        when(channelRouter.parseWechatCallback(anyMap(), anyString()))
                .thenReturn(wechatResult("PAY-1", false, "CLOSED"));

        payManager.handleWechatCallback(Map.of(), "body");

        verify(payOrderService, never()).markSuccessIfPending(anyString(), any());
        verify(payOrderService, never()).getByPayOrderNo(anyString());
        verify(orderClient, never()).updateOrderStatus(anyString(), anyInt());
    }

    @Test
    @DisplayName("handleWechatCallback — 解析成功但支付单不存在：抛 ORDER_NOT_FOUND 触发渠道重推")
    void shouldThrowOrderNotFound_whenPayOrderMissing() {
        when(channelRouter.parseWechatCallback(anyMap(), anyString()))
                .thenReturn(wechatResult("PAY-X", true, "SUCCESS"));
        when(payOrderService.getByPayOrderNo("PAY-X")).thenReturn(null);

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
    @DisplayName("handleAlipayCallback — 用支付宝验签器 + 表单参数提取（out_trade_no/trade_status）")
    void shouldUseAlipayVerifier() {
        when(channelRouter.verifyAlipayCallback(anyMap())).thenReturn(true);
        when(payOrderService.getByPayOrderNo("PAY-1"))
                .thenReturn(payOrder(PayStatusEnum.PENDING_PAY.getCode()));
        when(payOrderService.markSuccessIfPending(eq("PAY-1"), any())).thenReturn(true);

        payManager.handleAlipayCallback(Map.of("out_trade_no", "PAY-1", "trade_status", "TRADE_SUCCESS"));

        verify(channelRouter).verifyAlipayCallback(anyMap());
        verify(payOrderService).markSuccessIfPending("PAY-1", "TRADE_SUCCESS");
        verify(orderClient).updateOrderStatus("ORD-1", 2);
    }

    @Test
    @DisplayName("handleAlipayCallback — 验签失败抛 ILLEGAL_REQUEST 且不置成功")
    void shouldThrow_whenAlipaySignatureInvalid() {
        when(channelRouter.verifyAlipayCallback(anyMap())).thenReturn(false);

        BizException ex = assertThrows(BizException.class,
                () -> payManager.handleAlipayCallback(Map.of("out_trade_no", "PAY-1")));

        assertEquals(ErrorCode.ILLEGAL_REQUEST, ex.getErrorCode());
        verify(payOrderService, never()).markSuccessIfPending(anyString(), any());
    }

    // ==================== 超时关单 ====================

    @Test
    @DisplayName("cancelTimeoutPayOrders — 条件取消命中：同步订单为已取消")
    void shouldCancelTimeouts() {
        when(payOrderService.listTimeout(any(LocalDateTime.class)))
                .thenReturn(List.of(payOrder(PayStatusEnum.PENDING_PAY.getCode())));
        when(payOrderService.cancelPending(1L)).thenReturn(true);

        payManager.cancelTimeoutPayOrders();

        verify(orderClient).updateOrderStatus("ORD-1", 5);
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
