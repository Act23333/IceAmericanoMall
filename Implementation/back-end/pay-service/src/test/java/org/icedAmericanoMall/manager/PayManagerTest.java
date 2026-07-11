package org.icedAmericanoMall.manager;

import org.icedAmericanoMall.client.OrderClient;
import org.icedAmericanoMall.convert.PayOrderConverter;
import org.icedAmericanoMall.domain.entity.PayOrderEntity;
import org.icedAmericanoMall.domain.vo.PayOrderVO;
import org.icedAmericanoMall.dto.OrderSummaryDTO;
import org.icedAmericanoMall.enums.PayStatusEnum;
import org.icedAmericanoMall.integration.payment.PaymentClient;
import org.icedAmericanoMall.service.PayOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.noLazy.common.exception.BizException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PayManager 支付编排单元测试 —— Feign / 支付渠道 / 领域服务全部打桩。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PayManager 支付编排单元测试")
class PayManagerTest {

    @Mock PayOrderService payOrderService;
    @Mock PayOrderConverter payOrderConverter;
    @Mock OrderClient orderClient;
    @Mock PaymentClient paymentClient;
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
    @DisplayName("initiatePayment — 正常发起：取金额、生成二维码、创建待支付单")
    void shouldInitiate_whenOrderValid() {
        when(payOrderService.getByBizOrderNo("ORD-1")).thenReturn(null);
        when(orderClient.getOrder("ORD-1")).thenReturn(summary(12900));
        when(paymentClient.initiatePayment(eq("ORD-1"), eq(12900), anyString())).thenReturn("weixin://qr");
        when(payOrderService.createPending("ORD-1", 100L, 12900, "weixin://qr"))
                .thenReturn(payOrder(PayStatusEnum.PENDING_PAY.getCode()));

        assertNotNull(payManager.initiatePayment("ORD-1", 100L));
        verify(payOrderService).createPending("ORD-1", 100L, 12900, "weixin://qr");
    }

    @Test
    @DisplayName("initiatePayment — 已支付订单拒绝重复支付")
    void shouldReject_whenAlreadyPaid() {
        when(payOrderService.getByBizOrderNo("ORD-1"))
                .thenReturn(payOrder(PayStatusEnum.SUCCESS.getCode()));
        assertThrows(BizException.class, () -> payManager.initiatePayment("ORD-1", 100L));
        verify(orderClient, never()).getOrder(anyString());
    }

    @Test
    @DisplayName("initiatePayment — 订单不存在抛异常")
    void shouldReject_whenOrderNotFound() {
        when(payOrderService.getByBizOrderNo("ORD-1")).thenReturn(null);
        when(orderClient.getOrder("ORD-1")).thenReturn(null);
        assertThrows(BizException.class, () -> payManager.initiatePayment("ORD-1", 100L));
        verify(payOrderService, never()).createPending(anyString(), anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("handleCallback — 验签通过：置支付成功并更新订单为待发货")
    void shouldMarkSuccess_whenCallbackVerified() {
        when(paymentClient.verifyCallback(anyMap())).thenReturn(true);
        when(payOrderService.getByPayOrderNo("PAY-1"))
                .thenReturn(payOrder(PayStatusEnum.PENDING_PAY.getCode()));

        payManager.handleCallback(Map.of("out_trade_no", "PAY-1", "result_code", "SUCCESS"));

        verify(payOrderService).markSuccess(any(), eq("SUCCESS"));
        verify(orderClient).updateOrderStatus("ORD-1", 2);   // 待发货
    }

    @Test
    @DisplayName("handleCallback — 已成功单幂等跳过")
    void shouldSkip_whenAlreadySuccess() {
        when(paymentClient.verifyCallback(anyMap())).thenReturn(true);
        when(payOrderService.getByPayOrderNo("PAY-1"))
                .thenReturn(payOrder(PayStatusEnum.SUCCESS.getCode()));

        payManager.handleCallback(Map.of("out_trade_no", "PAY-1"));

        verify(payOrderService, never()).markSuccess(any(), any());
        verify(orderClient, never()).updateOrderStatus(anyString(), anyInt());
    }

    @Test
    @DisplayName("handleCallback — 验签失败抛异常")
    void shouldThrow_whenSignatureInvalid() {
        when(paymentClient.verifyCallback(anyMap())).thenReturn(false);
        assertThrows(BizException.class, () -> payManager.handleCallback(Map.of()));
        verify(payOrderService, never()).markSuccess(any(), any());
    }

    @Test
    @DisplayName("cancelTimeoutPayOrders — 逐单取消并同步订单为已取消")
    void shouldCancelTimeouts() {
        when(payOrderService.listTimeout(any(LocalDateTime.class)))
                .thenReturn(List.of(payOrder(PayStatusEnum.PENDING_PAY.getCode())));

        payManager.cancelTimeoutPayOrders();

        verify(payOrderService).markTimeoutCancel(1L);
        verify(orderClient).updateOrderStatus("ORD-1", 5);   // 已取消
    }
}
