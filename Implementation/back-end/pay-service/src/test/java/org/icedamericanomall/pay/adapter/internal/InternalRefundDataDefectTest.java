package org.icedamericanomall.pay.adapter.internal;

import org.icedamericanomall.client.AfterSaleClient;
import org.icedamericanomall.client.BalanceClient;
import org.icedamericanomall.client.NotificationClient;
import org.icedamericanomall.client.TradeInvoiceClient;
import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.pay.adapter.assembler.RefundConverterImpl;
import org.icedamericanomall.pay.app.OrderRefundManager;
import org.icedamericanomall.pay.app.PayEventNotifier;
import org.icedamericanomall.pay.app.PaymentChannelRouter;
import org.icedamericanomall.pay.app.RefundManager;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.domain.repository.PayOrderRepository;
import org.icedamericanomall.pay.domain.repository.RefundOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 退款内部端点的<b>「支付单存在但数据不完整」承载</b>（批次 R1）—— I40
 * {@code POST /internal/pay/refund} 与 I55 {@code POST /internal/pay/refund/order}。
 *
 * <p><b>为什么必须换承载</b>：批次 ① 把 {@code ORDER_NOT_FOUND(3009)} 的 HTTP 状态改成了 <b>404</b>，
 * 于是「支付单<b>存在</b>、但缺 {@code bizUserId}/{@code amount}」这三处对外表现为
 * <b>404 + 「支付单金额缺失」</b> —— 调用方与运维会读成「这笔支付单不存在」，真相是<b>这条数据坏了</b>。
 * {@code collaboration/09-constraints.md} §三·六 的四类划分里，这类属
 * <b>服务端数据不一致</b>：{@code INTERNAL_ERROR(1000)} / HTTP 500，{@code category=BUG}、
 * {@code retryable=false}（确定性失败，重试必然重复失败）。
 *
 * <p><b>内部面口径</b>：{@code /internal/**} 不包 {@code Result}，业务异常返回<b>真实 HTTP 状态码
 * + 裸 {@code ErrorInfo}</b>（{@code code/msg/category/retryable/traceId}，ADR-004）——
 * 故本类直接断言 {@code $.category}，锁死「不是 TRANSIENT / CLIENT」。
 *
 * <p><b>真实编排链</b>：端点背后挂的是真实 {@link RefundManager} / {@link OrderRefundManager}
 * （仓储与 Feign 客户端打桩、出参装配用真实 MapStruct 转换器），因此断言覆盖
 * 「Manager 抛什么 → 全局异常处理器回什么」全过程，而不是把异常直接塞给控制器。
 *
 * <p><b>改前必失败</b>：本批之前三条缺陷用例全部返回 <b>404 + 3009 + category=CLIENT</b>。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("退款内部端点 — 支付单数据不完整 → 500 + 1000 + BUG（不再是 404/3009）")
class InternalRefundDataDefectTest {

    private static final String PAY_ORDER_NO = "PAY-1";
    private static final String ORDER_NO = "ORD-1";
    private static final String SCENE_REF = "GB:ORD-1";
    private static final long BUYER_ID = 987654L;
    private static final int AMOUNT = 12800;

    /** I40 退款发起报文（售后单幂等键 + 订单号 + 退款金额）。 */
    private static final String I40_BODY = "{\"afterSaleId\":9,\"orderNo\":\"" + ORDER_NO
            + "\",\"refundAmount\":" + AMOUNT + ",\"reason\":\"质量问题\"}";

    /** I55 无售后单退款报文（sceneRef 幂等键）。 */
    private static final String I55_BODY = "{\"orderNo\":\"" + ORDER_NO + "\",\"sceneRef\":\"" + SCENE_REF
            + "\",\"reason\":\"拼团失败自动退款\"}";

    @Mock RefundOrderRepository refundOrderService;
    @Mock PayOrderRepository payOrderService;
    @Mock PaymentChannelRouter channelRouter;
    @Mock BalanceClient balanceClient;
    @Mock AfterSaleClient afterSaleClient;
    @Mock NotificationClient notificationClient;
    @Mock TradeInvoiceClient tradeInvoiceClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RefundManager refundManager = new RefundManager(refundOrderService, payOrderService, channelRouter,
                balanceClient, new RefundConverterImpl(),
                new PayEventNotifier(notificationClient, afterSaleClient, tradeInvoiceClient, payOrderService));
        OrderRefundManager orderRefundManager = new OrderRefundManager(refundOrderService, payOrderService,
                refundManager, new RefundConverterImpl());
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new InternalPayController(refundManager),
                        new InternalPayOrderRefundController(orderRefundManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private PayOrder payOrder(Long bizUserId, Integer amount) {
        PayOrder payOrder = new PayOrder();
        payOrder.setPayOrderNo(PAY_ORDER_NO);
        payOrder.setBizOrderNo(ORDER_NO);
        payOrder.setBizUserId(bizUserId);
        payOrder.setAmount(amount);
        payOrder.setPayChannelCode("WECHAT");
        return payOrder;
    }

    // ==================== 三处缺陷逐项：500 / 1000 / BUG / 不可重试 ====================

    @Test
    @DisplayName("I40 支付单缺少支付用户 → 500 + 1000 + BUG + retryable=false（不是 404/3009）")
    void shouldReturnBug500_whenI40PayOrderLacksPayee() throws Exception {
        when(refundOrderService.getByAfterSaleId(9L)).thenReturn(null);
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(payOrder(null, AMOUNT));

        mockMvc.perform(MockMvcRequestBuilders.post("/internal/pay/refund")
                        .contentType(MediaType.APPLICATION_JSON).content(I40_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(result -> assertNotEquals(404, result.getResponse().getStatus(),
                        "数据缺陷不得表现为「资源不存在」"))
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.category").value(ErrorCategory.BUG.name()))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.msg").value(allOf(
                        containsString("支付单数据不完整"),
                        containsString("缺少支付用户"),
                        containsString(PAY_ORDER_NO))));
        // fail-closed 语义不变：退不出去仍然退不出去，只是不再谎称「不存在」
        verify(refundOrderService, never()).createProcessing(any(), anyString(), anyString(), any(),
                anyString(), anyInt());
    }

    @Test
    @DisplayName("I55 支付单缺少金额 → 500 + 1000 + BUG + retryable=false（不是 404/3009）")
    void shouldReturnBug500_whenI55PayOrderLacksAmount() throws Exception {
        when(refundOrderService.getByBizRef(SCENE_REF)).thenReturn(null);
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(payOrder(BUYER_ID, null));

        mockMvc.perform(MockMvcRequestBuilders.post("/internal/pay/refund/order")
                        .contentType(MediaType.APPLICATION_JSON).content(I55_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(result -> assertNotEquals(404, result.getResponse().getStatus()))
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.category").value(ErrorCategory.BUG.name()))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.msg").value(allOf(
                        containsString("支付单数据不完整"),
                        containsString("缺少金额"),
                        containsString(PAY_ORDER_NO))));
        verify(refundOrderService, never()).createOrderRefundProcessing(any(), anyString(), any(), anyString(),
                anyInt(), anyString());
    }

    @Test
    @DisplayName("I55 支付单缺少支付用户 → 500 + 1000 + BUG + retryable=false（不是 404/3009）")
    void shouldReturnBug500_whenI55PayOrderLacksPayee() throws Exception {
        when(refundOrderService.getByBizRef(SCENE_REF)).thenReturn(null);
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(payOrder(null, AMOUNT));

        mockMvc.perform(MockMvcRequestBuilders.post("/internal/pay/refund/order")
                        .contentType(MediaType.APPLICATION_JSON).content(I55_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(result -> assertNotEquals(404, result.getResponse().getStatus()))
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.category").value(ErrorCategory.BUG.name()))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.msg").value(allOf(
                        containsString("支付单数据不完整"),
                        containsString("缺少支付用户"),
                        containsString(PAY_ORDER_NO))));
    }

    // ==================== 反向回归：真正的「支付单不存在」仍是 404 + 3009 ====================

    @Test
    @DisplayName("反向回归 I40 — 支付单真的不存在 → 仍 404 + 3009 + CLIENT（两类不得混成一种）")
    void shouldKeep404_whenI40PayOrderTrulyMissing() throws Exception {
        when(refundOrderService.getByAfterSaleId(9L)).thenReturn(null);
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(null);

        mockMvc.perform(MockMvcRequestBuilders.post("/internal/pay/refund")
                        .contentType(MediaType.APPLICATION_JSON).content(I40_BODY))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertNotEquals(500, result.getResponse().getStatus(),
                        "资源不存在不得升级成服务端缺陷"))
                .andExpect(jsonPath("$.code").value(3009))
                .andExpect(jsonPath("$.category").value(ErrorCategory.CLIENT.name()))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    @DisplayName("反向回归 I55 — 支付单真的不存在 → 仍 404 + 3009 + CLIENT（两类不得混成一种）")
    void shouldKeep404_whenI55PayOrderTrulyMissing() throws Exception {
        when(refundOrderService.getByBizRef(SCENE_REF)).thenReturn(null);
        when(payOrderService.getByBizOrderNo(ORDER_NO)).thenReturn(null);

        mockMvc.perform(MockMvcRequestBuilders.post("/internal/pay/refund/order")
                        .contentType(MediaType.APPLICATION_JSON).content(I55_BODY))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertNotEquals(500, result.getResponse().getStatus()))
                .andExpect(jsonPath("$.code").value(3009))
                .andExpect(jsonPath("$.category").value(ErrorCategory.CLIENT.name()));
    }
}
