package org.icedamericanomall.pay.app;

import org.icedamericanomall.client.AfterSaleClient;
import org.icedamericanomall.client.BalanceClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.BalanceRefundCO;
import org.icedamericanomall.integration.payment.ChannelRefundQueryResult;
import org.icedamericanomall.integration.payment.ChannelRefundState;
import org.icedamericanomall.integration.payment.ChannelRefundResult;
import org.icedamericanomall.pay.adapter.assembler.RefundConverterImpl;
import org.icedamericanomall.pay.domain.enums.PayChannelEnum;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.domain.model.refundorder.RefundOrder;
import org.icedamericanomall.pay.domain.repository.PayOrderRepository;
import org.icedamericanomall.pay.domain.repository.RefundOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * RefundResumeManager 悬挂退款补偿单元测试（ADR-035 风险收敛）。
 *
 * <p>覆盖：微信渠道查询三态收敛（SUCCESS 置成功回写 / FAILED 置失败 / PROCESSING 跳过）、
 * 余额与支付宝幂等重放结算、支付单缺失置失败、单笔异常不中断整批、扫描阈值与批量传参。
 * RefundManager 用真实实例（Mock 依赖 + 真实 MapStruct 转换器），锁死「补偿与 PAY6 同一落账路径」。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RefundResumeManager 悬挂退款补偿单元测试（ADR-035）")
class RefundResumeManagerTest {

    private static final String REFUND_NO_1 = "RFD-1";
    private static final String REFUND_NO_2 = "RFD-2";
    private static final String PAY_ORDER_NO = "PAY-1";
    private static final String ORDER_NO = "ORD-1";
    private static final long AFTER_SALE_ID_1 = 9L;
    private static final long AFTER_SALE_ID_2 = 10L;
    private static final int AMOUNT = 12800;

    @Mock RefundOrderRepository refundOrderService;
    @Mock PayOrderRepository payOrderService;
    @Mock PaymentChannelRouter channelRouter;
    @Mock BalanceClient balanceClient;
    @Mock AfterSaleClient afterSaleClient;

    private RefundResumeManager resumeManager;

    @BeforeEach
    void setUp() {
        RefundManager refundManager = new RefundManager(refundOrderService, payOrderService, channelRouter,
                balanceClient, afterSaleClient, new RefundConverterImpl());
        resumeManager = new RefundResumeManager(refundOrderService, payOrderService, channelRouter, refundManager);
    }

    private RefundOrder refundOrder(String refundNo, long afterSaleId, int status, String channel) {
        RefundOrder entity = new RefundOrder();
        entity.setId(1L);
        entity.setRefundNo(refundNo);
        entity.setAfterSaleId(afterSaleId);
        entity.setOrderNo(ORDER_NO);
        entity.setPayOrderNo(PAY_ORDER_NO);
        entity.setUserId(100L);
        entity.setChannel(channel);
        entity.setRefundAmount(AMOUNT);
        entity.setStatus(status);
        return entity;
    }

    private PayOrder payOrder(String channel, int amount) {
        PayOrder payOrder = new PayOrder();
        payOrder.setId(1L);
        payOrder.setPayOrderNo(PAY_ORDER_NO);
        payOrder.setBizOrderNo(ORDER_NO);
        payOrder.setBizUserId(100L);
        payOrder.setPayChannelCode(channel);
        payOrder.setAmount(amount);
        return payOrder;
    }

    // ==================== 微信渠道：渠道查询三态收敛 ====================

    @Test
    @DisplayName("微信悬挂单 — 渠道查询 SUCCESS → 条件置成功 + 回填渠道退款单号 + I42 回写售后单")
    void shouldSettleSuccessAndNotify_whenWechatQueryReturnsSuccess() {
        when(refundOrderService.listStaleProcessing(any(), anyInt()))
                .thenReturn(List.of(refundOrder(REFUND_NO_1, AFTER_SALE_ID_1, 0, "WECHAT")));
        when(channelRouter.queryWechatRefund(REFUND_NO_1))
                .thenReturn(new ChannelRefundQueryResult("5000099", ChannelRefundState.SUCCESS));
        when(refundOrderService.markSuccessIfProcessing(REFUND_NO_1, "5000099")).thenReturn(true);
        when(refundOrderService.getByRefundNo(REFUND_NO_1))
                .thenReturn(refundOrder(REFUND_NO_1, AFTER_SALE_ID_1, 1, "WECHAT"));

        resumeManager.resumeStuckRefunds();

        verify(refundOrderService).markSuccessIfProcessing(REFUND_NO_1, "5000099");
        verify(afterSaleClient).markRefunded(AFTER_SALE_ID_1);
    }

    @Test
    @DisplayName("微信悬挂单 — 渠道查询 FAILED → 置失败且 failReason 含归一化状态与渠道退款单号（AD43 可重试）")
    void shouldMarkFailedWithStateReason_whenWechatQueryReturnsFailed() {
        when(refundOrderService.listStaleProcessing(any(), anyInt()))
                .thenReturn(List.of(refundOrder(REFUND_NO_1, AFTER_SALE_ID_1, 0, "WECHAT")));
        when(channelRouter.queryWechatRefund(REFUND_NO_1))
                .thenReturn(new ChannelRefundQueryResult("5000099", ChannelRefundState.FAILED));

        resumeManager.resumeStuckRefunds();

        verify(refundOrderService).markFailedIfProcessing(REFUND_NO_1,
                "渠道退款查询终止: FAILED refundId=5000099");
        verify(refundOrderService, never()).markSuccessIfProcessing(anyString(), any());
        verify(afterSaleClient, never()).markRefunded(any());
    }

    @Test
    @DisplayName("微信悬挂单 — 渠道查询 PROCESSING → 不改状态本轮跳过（渠道仍在处理）")
    void shouldKeepProcessing_whenWechatQueryReturnsProcessing() {
        when(refundOrderService.listStaleProcessing(any(), anyInt()))
                .thenReturn(List.of(refundOrder(REFUND_NO_1, AFTER_SALE_ID_1, 0, "WECHAT")));
        when(channelRouter.queryWechatRefund(REFUND_NO_1))
                .thenReturn(new ChannelRefundQueryResult("5000099", ChannelRefundState.PROCESSING));

        resumeManager.resumeStuckRefunds();

        verify(refundOrderService, never()).markSuccessIfProcessing(anyString(), any());
        verify(refundOrderService, never()).markFailedIfProcessing(anyString(), anyString());
        verify(afterSaleClient, never()).markRefunded(any());
    }

    // ==================== 余额 / 支付宝渠道：幂等重放收敛 ====================

    @Test
    @DisplayName("余额悬挂单 — 重放 I41（transactionNo=refundNo）→ 置成功 + 回写售后单")
    void shouldReplayAndSettle_whenBalanceStuck() {
        when(refundOrderService.listStaleProcessing(any(), anyInt()))
                .thenReturn(List.of(refundOrder(REFUND_NO_1, AFTER_SALE_ID_1, 0, "BALANCE")));
        when(payOrderService.getByPayOrderNo(PAY_ORDER_NO)).thenReturn(payOrder("BALANCE", AMOUNT));
        when(refundOrderService.markSuccessIfProcessing(eq(REFUND_NO_1), any())).thenReturn(true);
        when(refundOrderService.getByRefundNo(REFUND_NO_1))
                .thenReturn(refundOrder(REFUND_NO_1, AFTER_SALE_ID_1, 1, "BALANCE"));

        resumeManager.resumeStuckRefunds();

        ArgumentCaptor<BalanceRefundCO> captor = ArgumentCaptor.forClass(BalanceRefundCO.class);
        verify(balanceClient).refund(captor.capture());
        assertEquals(REFUND_NO_1, captor.getValue().getTransactionNo());
        assertEquals(100L, captor.getValue().getUserId());
        assertEquals(AMOUNT, captor.getValue().getAmount());
        verify(refundOrderService).markSuccessIfProcessing(REFUND_NO_1, null);
        verify(afterSaleClient).markRefunded(AFTER_SALE_ID_1);
        verify(channelRouter, never()).refund(any(), anyString(), anyString(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("支付宝悬挂单 — 重放渠道退款（同步成功）→ 置成功 + 回写售后单，不走余额入账")
    void shouldReplayAndSettle_whenAlipayStuck() {
        when(refundOrderService.listStaleProcessing(any(), anyInt()))
                .thenReturn(List.of(refundOrder(REFUND_NO_1, AFTER_SALE_ID_1, 0, "ALIPAY")));
        when(payOrderService.getByPayOrderNo(PAY_ORDER_NO)).thenReturn(payOrder("ALIPAY", AMOUNT));
        when(channelRouter.refund(eq(PayChannelEnum.ALIPAY), eq(PAY_ORDER_NO), eq(REFUND_NO_1),
                eq(AMOUNT), eq(AMOUNT), isNull())).thenReturn(ChannelRefundResult.settled("alipay-rfd-1"));
        when(refundOrderService.markSuccessIfProcessing(REFUND_NO_1, "alipay-rfd-1")).thenReturn(true);
        when(refundOrderService.getByRefundNo(REFUND_NO_1))
                .thenReturn(refundOrder(REFUND_NO_1, AFTER_SALE_ID_1, 1, "ALIPAY"));

        resumeManager.resumeStuckRefunds();

        verify(refundOrderService).markSuccessIfProcessing(REFUND_NO_1, "alipay-rfd-1");
        verify(afterSaleClient).markRefunded(AFTER_SALE_ID_1);
        verifyNoInteractions(balanceClient);
    }

    @Test
    @DisplayName("重放前置 — 支付单不存在 → 置失败「支付单不存在」，不发起渠道调用（AD43 可见）")
    void shouldMarkFailed_whenPayOrderMissingOnReplay() {
        when(refundOrderService.listStaleProcessing(any(), anyInt()))
                .thenReturn(List.of(refundOrder(REFUND_NO_1, AFTER_SALE_ID_1, 0, "BALANCE")));
        when(payOrderService.getByPayOrderNo(PAY_ORDER_NO)).thenReturn(null);

        resumeManager.resumeStuckRefunds();

        verify(refundOrderService).markFailedIfProcessing(REFUND_NO_1, "支付单不存在");
        verifyNoInteractions(balanceClient, afterSaleClient);
        verify(channelRouter, never()).refund(any(), anyString(), anyString(), anyInt(), anyInt(), any());
    }

    // ==================== 批量纪律与扫描口径 ====================

    @Test
    @DisplayName("批量纪律 — 第一笔微信查询异常仅告警保持处理中，第二笔余额重放仍被处理")
    void shouldContinueBatch_whenFirstItemThrows() {
        when(refundOrderService.listStaleProcessing(any(), anyInt()))
                .thenReturn(List.of(
                        refundOrder(REFUND_NO_1, AFTER_SALE_ID_1, 0, "WECHAT"),
                        refundOrder(REFUND_NO_2, AFTER_SALE_ID_2, 0, "BALANCE")));
        when(channelRouter.queryWechatRefund(REFUND_NO_1))
                .thenThrow(new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "微信退款查询失败"));
        when(payOrderService.getByPayOrderNo(PAY_ORDER_NO)).thenReturn(payOrder("BALANCE", AMOUNT));
        when(refundOrderService.markSuccessIfProcessing(eq(REFUND_NO_2), any())).thenReturn(true);
        when(refundOrderService.getByRefundNo(REFUND_NO_2))
                .thenReturn(refundOrder(REFUND_NO_2, AFTER_SALE_ID_2, 1, "BALANCE"));

        int scanned = resumeManager.resumeStuckRefunds();

        assertEquals(2, scanned);
        ArgumentCaptor<BalanceRefundCO> captor = ArgumentCaptor.forClass(BalanceRefundCO.class);
        verify(balanceClient).refund(captor.capture());
        assertEquals(REFUND_NO_2, captor.getValue().getTransactionNo());
        verify(afterSaleClient).markRefunded(AFTER_SALE_ID_2);
        // 第一笔保持处理中：未置失败也未置成功
        verify(refundOrderService, never()).markFailedIfProcessing(eq(REFUND_NO_1), anyString());
        verify(refundOrderService, never()).markSuccessIfProcessing(eq(REFUND_NO_1), any());
    }

    @Test
    @DisplayName("扫描口径 — 悬挂阈值=now-10分钟、单轮上限=100 正确传入仓储")
    void shouldPassThresholdAndLimit_whenScanningStale() {
        when(refundOrderService.listStaleProcessing(any(), anyInt())).thenReturn(List.of());
        LocalDateTime lowerBound = LocalDateTime.now().minusMinutes(
                RefundResumeManager.STALE_THRESHOLD_MINUTES).minusSeconds(5);

        resumeManager.resumeStuckRefunds();

        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(refundOrderService).listStaleProcessing(thresholdCaptor.capture(), limitCaptor.capture());
        assertTrue(thresholdCaptor.getValue().isAfter(lowerBound));
        assertEquals(RefundResumeManager.BATCH_LIMIT, limitCaptor.getValue());
    }

    @Test
    @DisplayName("未知渠道 — 告警跳过，不查渠道、不重放、不置失败（需人工介入）")
    void shouldSkipWithoutStateChange_whenChannelUnknown() {
        when(refundOrderService.listStaleProcessing(any(), anyInt()))
                .thenReturn(List.of(refundOrder(REFUND_NO_1, AFTER_SALE_ID_1, 0, "UNKNOWN_CHANNEL")));

        resumeManager.resumeStuckRefunds();

        verifyNoInteractions(channelRouter, payOrderService, balanceClient, afterSaleClient);
        verify(refundOrderService, never()).markFailedIfProcessing(anyString(), anyString());
        verify(refundOrderService, never()).markSuccessIfProcessing(anyString(), any());
    }

    @Test
    @DisplayName("无悬挂单 — 空批次返回 0，不触发任何渠道/仓储写操作")
    void shouldReturnZero_whenNoStaleRefunds() {
        when(refundOrderService.listStaleProcessing(any(), anyInt())).thenReturn(List.of());

        int scanned = resumeManager.resumeStuckRefunds();

        assertEquals(0, scanned);
        verify(refundOrderService, never()).markSuccessIfProcessing(anyString(), any());
        verify(refundOrderService, never()).markFailedIfProcessing(anyString(), anyString());
    }
}
