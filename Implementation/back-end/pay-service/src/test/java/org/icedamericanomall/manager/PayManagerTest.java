package org.icedamericanomall.manager;

import org.icedamericanomall.client.OrderClient;
import org.icedamericanomall.client.UserClient;
import org.icedamericanomall.convert.PayOrderConverter;
import org.icedamericanomall.domain.entity.PayOrderEntity;
import org.icedamericanomall.domain.vo.PayOrderVO;
import org.icedamericanomall.dto.OrderSummaryDTO;
import org.icedamericanomall.enums.PayChannelEnum;
import org.icedamericanomall.enums.PayStatusEnum;
import org.icedamericanomall.enums.PayTypeEnum;
import org.icedamericanomall.integration.payment.AlipayPaymentClient;
import org.icedamericanomall.integration.payment.PaymentClient;
import org.icedamericanomall.service.PayOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PayManager 支付编排单元测试 —— 微信/支付宝/余额三渠道；Feign / 支付渠道 / 领域服务全部打桩。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PayManager 支付编排单元测试")
class PayManagerTest {

    @Mock PayOrderService payOrderService;
    @Mock PayOrderConverter payOrderConverter;
    @Mock OrderClient orderClient;
    @Mock PaymentChannelRouter channelRouter;
    @InjectMocks PayManager payManager;

    private OrderSummaryDTO summary(int amount) {
        OrderSummaryDTO s = new OrderSummaryDTO();
        s.setOrderNo("ORD-1"); s.setTotalAmount(amount); s.setStatus(1);
        return s;
    }

    private PayOrderEntity payOrder(int status) {
        PayOrderEntity p = new PayOrderEntity();
        p.setId(1L); p.setBizOrderNo("ORD-1"); p.setPayOrderNo("PAY-1"); p.setStatus(status);
        return p;
    }

    @BeforeEach
    void stubConverter() {
        lenient().when(payOrderConverter.toVO(any())).thenReturn(new PayOrderVO());
    }

    @Test
    @DisplayName("initiatePayment(WECHAT) — 取金额、生成二维码、创建待支付单")
    void shouldInitiateWechat_whenOrderValid() {
        when(payOrderService.getByBizOrderNo("ORD-1")).thenReturn(null);
        when(orderClient.getOrder("ORD-1")).thenReturn(summary(12900));
        when(channelRouter.initiatePayment(eq("ORD-1"), eq(12900), anyString(), eq(PayChannelEnum.WECHAT)))
                .thenReturn("weixin://qr");
        when(payOrderService.createPending("ORD-1", 100L, 12900, "weixin://qr", "WECHAT", PayTypeEnum.NATIVE.getCode()))
                .thenReturn(payOrder(PayStatusEnum.PENDING_PAY.getCode()));

        assertNotNull(payManager.initiatePayment("ORD-1", 100L, PayChannelEnum.WECHAT));
        verify(payOrderService).createPending("ORD-1", 100L, 12900, "weixin://qr", "WECHAT", PayTypeEnum.NATIVE.getCode());
    }

    @Test
    @DisplayName("initiatePayment(ALIPAY) — 走支付宝客户端拿链接")
    void shouldInitiateAlipay() {
        when(payOrderService.getByBizOrderNo("ORD-1")).thenReturn(null);
        when(orderClient.getOrder("ORD-1")).thenReturn(summary(8800));
        when(channelRouter.initiatePayment(eq("ORD-1"), eq(8800), anyString(), eq(PayChannelEnum.ALIPAY)))
                .thenReturn("https://alipay/pay");
        when(payOrderService.createPending("ORD-1", 100L, 8800, "https://alipay/pay", "ALIPAY", PayTypeEnum.NATIVE.getCode()))
                .thenReturn(payOrder(PayStatusEnum.PENDING_PAY.getCode()));

        assertNotNull(payManager.initiatePayment("ORD-1", 100L, PayChannelEnum.ALIPAY));
        verify(channelRouter).initiatePayment(eq("ORD-1"), eq(8800), anyString(), eq(PayChannelEnum.ALIPAY));
    }

    @Test
    @DisplayName("initiatePayment(BALANCE) — 扣款成功直接成单并通知待发货")
    void shouldPayByBalance() {
        when(payOrderService.getByBizOrderNo("ORD-1")).thenReturn(null);
        when(orderClient.getOrder("ORD-1")).thenReturn(summary(5000));
        when(payOrderService.createPaidByBalance("ORD-1", 100L, 5000))
                .thenReturn(payOrder(PayStatusEnum.SUCCESS.getCode()));

        payManager.initiatePayment("ORD-1", 100L, PayChannelEnum.BALANCE);

        verify(channelRouter).deductBalance(100L, 5000);
        verify(payOrderService).createPaidByBalance("ORD-1", 100L, 5000);
        verify(orderClient).updateOrderStatus("ORD-1", 2);   // 待发货
    }

    @Test
    @DisplayName("initiatePayment(BALANCE) — 余额不足：抛异常且不建单")
    void shouldReject_whenBalanceInsufficient() {
        when(payOrderService.getByBizOrderNo("ORD-1")).thenReturn(null);
        when(orderClient.getOrder("ORD-1")).thenReturn(summary(5000));
        doThrow(new BizException(ErrorCode.BALANCE_INSUFFICIENT, "余额不足"))
                .when(channelRouter).deductBalance(100L, 5000);

        assertThrows(BizException.class, () -> payManager.initiatePayment("ORD-1", 100L, PayChannelEnum.BALANCE));
        verify(payOrderService, never()).createPaidByBalance(anyString(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("initiatePayment — 已支付订单拒绝重复支付")
    void shouldReject_whenAlreadyPaid() {
        when(payOrderService.getByBizOrderNo("ORD-1"))
                .thenReturn(payOrder(PayStatusEnum.SUCCESS.getCode()));
        assertThrows(BizException.class, () -> payManager.initiatePayment("ORD-1", 100L, PayChannelEnum.WECHAT));
        verify(orderClient, never()).getOrder(anyString());
    }

    @Test
    @DisplayName("handleCallback(WECHAT) — 验签通过：置成功并更新订单为待发货")
    void shouldMarkSuccess_whenCallbackVerified() {
        when(channelRouter.verifyCallback(anyMap(), eq(PayChannelEnum.WECHAT))).thenReturn(true);
        when(payOrderService.getByPayOrderNo("PAY-1"))
                .thenReturn(payOrder(PayStatusEnum.PENDING_PAY.getCode()));

        payManager.handleCallback(Map.of("out_trade_no", "PAY-1", "result_code", "SUCCESS"), PayChannelEnum.WECHAT);

        verify(payOrderService).markSuccess(any(), eq("SUCCESS"));
        verify(orderClient).updateOrderStatus("ORD-1", 2);
    }

    @Test
    @DisplayName("handleCallback(ALIPAY) — 用支付宝验签器")
    void shouldUseAlipayVerifier() {
        when(channelRouter.verifyCallback(anyMap(), eq(PayChannelEnum.ALIPAY))).thenReturn(true);
        when(payOrderService.getByPayOrderNo("PAY-1"))
                .thenReturn(payOrder(PayStatusEnum.PENDING_PAY.getCode()));

        payManager.handleCallback(Map.of("out_trade_no", "PAY-1"), PayChannelEnum.ALIPAY);

        verify(channelRouter).verifyCallback(anyMap(), eq(PayChannelEnum.ALIPAY));
        verify(payOrderService).markSuccess(any(), any());
    }

    @Test
    @DisplayName("handleCallback — 验签失败抛异常")
    void shouldThrow_whenSignatureInvalid() {
        when(channelRouter.verifyCallback(anyMap(), eq(PayChannelEnum.WECHAT))).thenReturn(false);
        assertThrows(BizException.class, () -> payManager.handleCallback(Map.of(), PayChannelEnum.WECHAT));
        verify(payOrderService, never()).markSuccess(any(), any());
    }

    @Test
    @DisplayName("cancelTimeoutPayOrders — 逐单取消并同步订单为已取消")
    void shouldCancelTimeouts() {
        when(payOrderService.listTimeout(any(LocalDateTime.class)))
                .thenReturn(List.of(payOrder(PayStatusEnum.PENDING_PAY.getCode())));

        payManager.cancelTimeoutPayOrders();

        verify(payOrderService).markTimeoutCancel(1L);
        verify(orderClient).updateOrderStatus("ORD-1", 5);
    }
}
