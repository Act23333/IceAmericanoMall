package org.icedamericanomall.pay.app;

import org.icedamericanomall.client.TradeInvoiceClient;
import org.icedamericanomall.common.api.ErrorInfo;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.InvoicePartialRefundCO;
import org.icedamericanomall.dto.InvoiceVoidCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.pay.domain.enums.InvoiceLinkCompensationStatusEnum;
import org.icedamericanomall.pay.domain.enums.InvoiceLinkKindEnum;
import org.icedamericanomall.pay.domain.model.invoicelink.InvoiceLinkCompensation;
import org.icedamericanomall.pay.domain.repository.InvoiceLinkCompensationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * InvoiceLinkCompensationRetryManager 单元测试 —— 补偿重试任务的分派语义
 * （{@code invoice_link_compensation} 的消费者，本批新增）。
 *
 * <p><b>为什么用 Mockito 而不是 H2</b>：本类被测的是<b>决策</b>（成功置 DONE / 可重试退避 /
 * 次数耗尽终态 / 下游 4xx 直接终态 / 单条异常不中断整批），输入是「到期行 + 重调结果」，
 * 输出是「对仓储端口的哪一次条件更新调用」。条件更新本身（{@code WHERE status = PENDING}
 * 真的在库里生效）由 {@code InvoiceLinkCompensationRetryH2Test} 用真实 H2 证明 —— 二者缺一，
 * 「幂等补偿」都只是口头承诺。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InvoiceLinkCompensationRetryManager 补偿重试编排测试")
class InvoiceLinkCompensationRetryManagerTest {

    /** 订单号（32 位 hex 线格式业务键，与 {@code IdGenerator#toWire} 产出同形态）。 */
    private static final String ORDER_NO = "bbbbbbbb000070008000000000000001";
    private static final String OTHER_ORDER_NO = "bbbbbbbb000070008000000000000002";
    private static final String SCENE_REF = "AS:9";
    private static final long ROW_ID = 11L;
    private static final long OTHER_ROW_ID = 12L;

    @Mock
    private InvoiceLinkCompensationRepository invoiceLinkCompensationRepository;

    @Mock
    private TradeInvoiceClient tradeInvoiceClient;

    @InjectMocks
    private InvoiceLinkCompensationRetryManager retryManager;

    /** 到期补偿行（{@code PENDING}，{@code retryCount} 由用例给定）。 */
    private InvoiceLinkCompensation dueRow(long id, String orderNo, InvoiceLinkKindEnum kind, int retryCount) {
        InvoiceLinkCompensation record = InvoiceLinkCompensation.newPending(orderNo, SCENE_REF, kind,
                "BizException[SYSTEM_BUSY]: 上一次联动失败");
        record.setId(id);
        record.setRetryCount(retryCount);
        return record;
    }

    /** 下游 4xx 的真实形态：降级工厂把 {@code UpstreamException}（category=CLIENT）原样抛出（§三·八 透传）。 */
    private static UpstreamException downstreamClientError() {
        return new UpstreamException(
                ErrorInfo.of(1103, "订单不存在", ErrorCategory.CLIENT, null), 404);
    }

    // ==================== ① 到期行被拾取并重调对应 Feign 方法 ====================

    @Test
    @DisplayName("拾取到期行（VOID）— 重调 voidOnFullRefund：orderNo/sceneRef 与落库口径一致；不误调部分退款接口")
    void shouldRedispatchVoid_whenDueRowKindIsVoid() {
        when(invoiceLinkCompensationRepository.findDueForRetry(InvoiceLinkCompensationRetryManager.BATCH_LIMIT))
                .thenReturn(List.of(dueRow(ROW_ID, ORDER_NO, InvoiceLinkKindEnum.VOID, 0)));

        int scanned = retryManager.retryDueCompensations();

        assertEquals(1, scanned);
        ArgumentCaptor<InvoiceVoidCO> captor = ArgumentCaptor.forClass(InvoiceVoidCO.class);
        verify(tradeInvoiceClient).voidOnFullRefund(captor.capture());
        assertEquals(ORDER_NO, captor.getValue().getOrderNo(), "order_no 必须还原成 32-hex 线格式再传下游（ADR-062）");
        assertEquals(SCENE_REF, captor.getValue().getSceneRef(), "sceneRef 必须 = refund_order.biz_ref");
        verify(tradeInvoiceClient, never()).markPartialRefund(any(InvoicePartialRefundCO.class));
        verify(invoiceLinkCompensationRepository).markDone(ROW_ID);
    }

    @Test
    @DisplayName("拾取到期行（PARTIAL）— 重调 markPartialRefund：路径与 VOID 互斥")
    void shouldRedispatchPartial_whenDueRowKindIsPartial() {
        when(invoiceLinkCompensationRepository.findDueForRetry(InvoiceLinkCompensationRetryManager.BATCH_LIMIT))
                .thenReturn(List.of(dueRow(ROW_ID, ORDER_NO, InvoiceLinkKindEnum.PARTIAL, 0)));

        retryManager.retryDueCompensations();

        ArgumentCaptor<InvoicePartialRefundCO> captor = ArgumentCaptor.forClass(InvoicePartialRefundCO.class);
        verify(tradeInvoiceClient).markPartialRefund(captor.capture());
        assertEquals(ORDER_NO, captor.getValue().getOrderNo());
        assertEquals(SCENE_REF, captor.getValue().getSceneRef());
        verify(tradeInvoiceClient, never()).voidOnFullRefund(any(InvoiceVoidCO.class));
        verify(invoiceLinkCompensationRepository).markDone(ROW_ID);
    }

    // ==================== ② 成功后置 DONE 且不再被拾取 ====================

    @Test
    @DisplayName("重调成功 — 条件置 DONE；下一轮已无到期行，故不再重调（同一行只补偿一次）")
    void shouldMarkDoneAndNotPickAgain_whenRetrySucceeds() {
        InvoiceLinkCompensation row = dueRow(ROW_ID, ORDER_NO, InvoiceLinkKindEnum.VOID, 0);
        // 第二轮：该行已 DONE，findDueForRetry 不再返回它（条件更新 + status 筛选的效果）
        when(invoiceLinkCompensationRepository.findDueForRetry(InvoiceLinkCompensationRetryManager.BATCH_LIMIT))
                .thenReturn(List.of(row), List.of());
        when(invoiceLinkCompensationRepository.markDone(ROW_ID)).thenReturn(true);

        retryManager.retryDueCompensations();
        int secondRound = retryManager.retryDueCompensations();

        assertEquals(0, secondRound, "已置 DONE 的行不得再被拾取");
        verify(tradeInvoiceClient, times(1)).voidOnFullRefund(any(InvoiceVoidCO.class));
        verify(invoiceLinkCompensationRepository, times(1)).markDone(ROW_ID);
        verify(invoiceLinkCompensationRepository, never())
                .scheduleRetry(anyLong(), anyInt(), any(LocalDateTime.class), anyString());
    }

    // ==================== ③ 可重试失败 → 退避 ====================

    @Test
    @DisplayName("可重试失败（TRANSIENT）— retry_count+1、next_retry_at 前移 2 分钟、last_error 落类别与摘要")
    void shouldScheduleRetryWithBackoff_whenFailureIsTransient() {
        InvoiceLinkCompensation row = dueRow(ROW_ID, ORDER_NO, InvoiceLinkKindEnum.VOID, 0);
        when(invoiceLinkCompensationRepository.findDueForRetry(InvoiceLinkCompensationRetryManager.BATCH_LIMIT))
                .thenReturn(List.of(row));
        doThrow(new BizException(ErrorCode.SYSTEM_BUSY, "发票联动暂时无法完成"))
                .when(tradeInvoiceClient).voidOnFullRefund(any(InvoiceVoidCO.class));

        LocalDateTime before = LocalDateTime.now();
        retryManager.retryDueCompensations();

        ArgumentCaptor<LocalDateTime> nextRetryCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<String> lastErrorCaptor = ArgumentCaptor.forClass(String.class);
        verify(invoiceLinkCompensationRepository).scheduleRetry(
                eq(ROW_ID), eq(1), nextRetryCaptor.capture(), lastErrorCaptor.capture());
        LocalDateTime nextRetryAt = nextRetryCaptor.getValue();
        assertFalse(nextRetryAt.isBefore(before.plusMinutes(2)),
                "首次失败退避必须 >= 2 分钟（2^1），实际: " + nextRetryAt);
        assertFalse(nextRetryAt.isAfter(LocalDateTime.now().plusMinutes(2)),
                "首次失败退避必须 <= 2 分钟 + 执行耗时，实际: " + nextRetryAt);
        assertTrue(lastErrorCaptor.getValue().contains("SYSTEM_BUSY"),
                "last_error 须带业务错误码: " + lastErrorCaptor.getValue());
        assertTrue(lastErrorCaptor.getValue().contains("category=TRANSIENT"),
                "last_error 须带处置类别，便于区分自愈/需人工: " + lastErrorCaptor.getValue());
        verify(invoiceLinkCompensationRepository, never()).markDone(anyLong());
        verify(invoiceLinkCompensationRepository, never()).markFailedPermanent(anyLong(), anyString());
    }

    @Test
    @DisplayName("可重试失败（拿不到分类）— 未知按可重试处理（绝不静态放弃），同样进退避")
    void shouldScheduleRetry_whenFailureCategoryUnknown() {
        InvoiceLinkCompensation row = dueRow(ROW_ID, ORDER_NO, InvoiceLinkKindEnum.VOID, 1);
        when(invoiceLinkCompensationRepository.findDueForRetry(InvoiceLinkCompensationRetryManager.BATCH_LIMIT))
                .thenReturn(List.of(row));
        doThrow(new IllegalStateException("契约违约"))
                .when(tradeInvoiceClient).voidOnFullRefund(any(InvoiceVoidCO.class));

        retryManager.retryDueCompensations();

        ArgumentCaptor<LocalDateTime> nextRetryCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(invoiceLinkCompensationRepository).scheduleRetry(
                eq(ROW_ID), eq(2), nextRetryCaptor.capture(), anyString());
        assertFalse(nextRetryCaptor.getValue().isBefore(LocalDateTime.now().plusMinutes(3)),
                "第 2 次失败退避必须 >= 4 分钟 - 1 分钟容差（2^2）");
        verify(invoiceLinkCompensationRepository, never()).markFailedPermanent(anyLong(), anyString());
    }

    // ==================== ④ 次数耗尽 → 永久失败 ====================

    @Test
    @DisplayName("重试次数耗尽 — retry_count+1 >= MAX_RETRY：置 FAILED_PERMANENT，不再退避（不写 next_retry_at）")
    void shouldMarkFailedPermanent_whenRetryCountExhausted() {
        InvoiceLinkCompensation row = dueRow(ROW_ID, ORDER_NO, InvoiceLinkKindEnum.VOID,
                InvoiceLinkCompensationRetryManager.MAX_RETRY - 1);
        when(invoiceLinkCompensationRepository.findDueForRetry(InvoiceLinkCompensationRetryManager.BATCH_LIMIT))
                .thenReturn(List.of(row));
        doThrow(new BizException(ErrorCode.SYSTEM_BUSY, "发票联动暂时无法完成"))
                .when(tradeInvoiceClient).voidOnFullRefund(any(InvoiceVoidCO.class));

        retryManager.retryDueCompensations();

        ArgumentCaptor<String> lastErrorCaptor = ArgumentCaptor.forClass(String.class);
        verify(invoiceLinkCompensationRepository).markFailedPermanent(eq(ROW_ID), lastErrorCaptor.capture());
        assertTrue(lastErrorCaptor.getValue().contains("SYSTEM_BUSY"));
        verify(invoiceLinkCompensationRepository, never())
                .scheduleRetry(anyLong(), anyInt(), any(LocalDateTime.class), anyString());
        verify(invoiceLinkCompensationRepository, never()).markDone(anyLong());
    }

    // ==================== ⑤ 下游 4xx（CLIENT）→ 直接终态，不进退避 ====================

    @Test
    @DisplayName("下游 4xx（category=CLIENT，重试必然重复失败）— 首次失败即置 FAILED_PERMANENT，不进退避")
    void shouldMarkFailedPermanentWithoutBackoff_whenDownstreamRejectsWith4xx() {
        InvoiceLinkCompensation row = dueRow(ROW_ID, ORDER_NO, InvoiceLinkKindEnum.PARTIAL, 0);
        when(invoiceLinkCompensationRepository.findDueForRetry(InvoiceLinkCompensationRetryManager.BATCH_LIMIT))
                .thenReturn(List.of(row));
        doThrow(downstreamClientError())
                .when(tradeInvoiceClient).markPartialRefund(any(InvoicePartialRefundCO.class));

        retryManager.retryDueCompensations();

        ArgumentCaptor<String> lastErrorCaptor = ArgumentCaptor.forClass(String.class);
        verify(invoiceLinkCompensationRepository).markFailedPermanent(eq(ROW_ID), lastErrorCaptor.capture());
        assertTrue(lastErrorCaptor.getValue().contains("category=CLIENT"),
                "终态摘要须记明是下游拒绝，便于人工判断不必重试: " + lastErrorCaptor.getValue());
        assertTrue(lastErrorCaptor.getValue().contains("1103"),
                "终态摘要须保留下游业务码（透传不改写）: " + lastErrorCaptor.getValue());
        verify(invoiceLinkCompensationRepository, never())
                .scheduleRetry(anyLong(), anyInt(), any(LocalDateTime.class), anyString());
        verify(invoiceLinkCompensationRepository, never()).markDone(anyLong());
    }

    // ==================== ⑥ 没有到期行 → 不调用任何 Feign ====================

    @Test
    @DisplayName("无到期行 — 不调用任何 Feign、不写任何状态，返回 0")
    void shouldNotCallFeign_whenNoDueRow() {
        when(invoiceLinkCompensationRepository.findDueForRetry(InvoiceLinkCompensationRetryManager.BATCH_LIMIT))
                .thenReturn(List.of());

        int scanned = retryManager.retryDueCompensations();

        assertEquals(0, scanned);
        verifyNoInteractions(tradeInvoiceClient);
        verify(invoiceLinkCompensationRepository, never()).markDone(anyLong());
        verify(invoiceLinkCompensationRepository, never())
                .scheduleRetry(anyLong(), anyInt(), any(LocalDateTime.class), anyString());
        verify(invoiceLinkCompensationRepository, never()).markFailedPermanent(anyLong(), anyString());
    }

    // ==================== ⑦ 单条抛错不影响同批其它行 ====================

    @Test
    @DisplayName("单条状态回写抛错（持久层故障）— 整批继续：后续行照常重调并置 DONE")
    void shouldContinueBatch_whenOneRowThrows() {
        InvoiceLinkCompensation failing = dueRow(ROW_ID, ORDER_NO, InvoiceLinkKindEnum.VOID, 0);
        InvoiceLinkCompensation healthy = dueRow(OTHER_ROW_ID, OTHER_ORDER_NO, InvoiceLinkKindEnum.VOID, 0);
        when(invoiceLinkCompensationRepository.findDueForRetry(InvoiceLinkCompensationRetryManager.BATCH_LIMIT))
                .thenReturn(List.of(failing, healthy));
        // 第 1 次重调失败（TRANSIENT）→ 进退避；第 2 次成功 → 置 DONE（证明整批没被前一行拖死）
        doThrow(new BizException(ErrorCode.SYSTEM_BUSY, "发票联动暂时无法完成"))
                .doNothing()
                .when(tradeInvoiceClient).voidOnFullRefund(any(InvoiceVoidCO.class));
        // 第一行：退避写入本身炸（数据库不可用）→ 必须由批量循环兜住，不得中断整批
        when(invoiceLinkCompensationRepository.scheduleRetry(eq(ROW_ID), anyInt(),
                any(LocalDateTime.class), anyString()))
                .thenThrow(new DataAccessResourceFailureException("invoice_link_compensation 不可用"));

        retryManager.retryDueCompensations();

        verify(tradeInvoiceClient, times(2)).voidOnFullRefund(any(InvoiceVoidCO.class));
        verify(invoiceLinkCompensationRepository).markDone(OTHER_ROW_ID);
        verify(invoiceLinkCompensationRepository, never()).markDone(ROW_ID);
    }

    // ==================== 退避函数（2^n 分钟，封顶） ====================

    @Test
    @DisplayName("退避函数 — 2^n 分钟；超过封顶常量后取值恒为 MAX_BACKOFF_MINUTES，且不溢出")
    void shouldComputeCappedExponentialBackoff_whenRetryCountGrows() {
        assertEquals(1L, InvoiceLinkCompensationRetryManager.backoffMinutes(0),
                "0 次退避按 2^0 = 1 分钟（不从 2 分钟起跳，避免首轮就压慢收敛）");
        assertEquals(2L, InvoiceLinkCompensationRetryManager.backoffMinutes(1));
        assertEquals(4L, InvoiceLinkCompensationRetryManager.backoffMinutes(2));
        assertEquals(8L, InvoiceLinkCompensationRetryManager.backoffMinutes(3));
        assertEquals(32L, InvoiceLinkCompensationRetryManager.backoffMinutes(
                InvoiceLinkCompensationRetryManager.MAX_RETRY), "2^5 = 32 分钟，仍在封顶之下");
        assertEquals(InvoiceLinkCompensationRetryManager.MAX_BACKOFF_MINUTES,
                InvoiceLinkCompensationRetryManager.backoffMinutes(6), "2^6 = 64 分钟被封顶到 MAX_BACKOFF_MINUTES");
        assertEquals(InvoiceLinkCompensationRetryManager.MAX_BACKOFF_MINUTES,
                InvoiceLinkCompensationRetryManager.backoffMinutes(Integer.MAX_VALUE), "极大重试次数不得溢出");
    }

    // ==================== 状态筛选口径（PENDING 才可被拾取） ====================

    @Test
    @DisplayName("DONE 行已不在待补偿态 — markDone 返回 false（幂等命中）时只记日志，不回退、不重复补偿")
    void shouldNotRewrite_whenMarkDoneMissesAlreadyDoneRow() {
        InvoiceLinkCompensation row = dueRow(ROW_ID, ORDER_NO, InvoiceLinkKindEnum.VOID, 0);
        when(invoiceLinkCompensationRepository.findDueForRetry(InvoiceLinkCompensationRetryManager.BATCH_LIMIT))
                .thenReturn(List.of(row));
        // 并发调度下另一执行者已置 DONE：条件更新命中 0 行
        when(invoiceLinkCompensationRepository.markDone(ROW_ID)).thenReturn(false);

        retryManager.retryDueCompensations();

        verify(invoiceLinkCompensationRepository).markDone(ROW_ID);
        verify(invoiceLinkCompensationRepository, never())
                .scheduleRetry(anyLong(), anyInt(), any(LocalDateTime.class), anyString());
        verify(invoiceLinkCompensationRepository, never()).markFailedPermanent(anyLong(), anyString());
        assertEquals(InvoiceLinkCompensationStatusEnum.PENDING.getCode(), row.getStatus(),
                "重试编排不得在内存里臆改聚合状态（状态迁移只走条件更新）");
        assertNotNull(row.getNextRetryAt());
    }
}
