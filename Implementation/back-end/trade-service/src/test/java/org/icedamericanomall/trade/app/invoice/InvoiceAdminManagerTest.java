package org.icedamericanomall.trade.app.invoice;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.crypto.AesGcmUtil;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;
import org.icedamericanomall.trade.adapter.assembler.InvoiceAssembler;
import org.icedamericanomall.trade.adapter.request.AdminInvoicePageReq;
import org.icedamericanomall.trade.adapter.response.InvoiceVO;
import org.icedamericanomall.trade.app.TradeEventNotifier;
import org.icedamericanomall.trade.domain.enums.InvoiceStatusEnum;
import org.icedamericanomall.trade.domain.model.invoice.Invoice;
import org.icedamericanomall.trade.domain.port.InvoiceIssueParam;
import org.icedamericanomall.trade.domain.port.InvoiceIssuePort;
import org.icedamericanomall.trade.domain.port.InvoiceIssueResult;
import org.icedamericanomall.trade.domain.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 管理端发票用例单元测试（AD57-AD60 + I56，F14）：开具（先开具后条件更新 + 触达顺序）/
 * 作废合规守卫 / 冲红幂等 / 全额退款联动（作废与冲红分支 + 幂等空操作）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InvoiceAdminManager — AD57-AD60 与 I56 联动编排")
class InvoiceAdminManagerTest {

    private static final String INVOICE_NO = "3f2a1b6c-0000-0000-0000-000000000001";
    private static final String ORDER_NO = "ORD-1";
    private static final long USER_ID = 100L;
    private static final String MOCK_INVOICE_NO = "MOCK-ABCDEF12";
    private static final String PDF_KEY = "invoice/" + INVOICE_NO + ".pdf";

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceIssuePort invoiceIssuePort;
    @Mock private TradeEventNotifier tradeEventNotifier;

    private InvoiceAdminManager manager;

    @BeforeAll
    static void initCrypto() {
        AesGcmUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
        BlindIndexUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
    }

    @BeforeEach
    void setUp() {
        manager = new InvoiceAdminManager(invoiceRepository, invoiceIssuePort,
                new InvoiceAssembler(), tradeEventNotifier);
    }

    private Invoice pendingInvoice() {
        Invoice invoice = Invoice.create(ORDER_NO, USER_ID, 200L, 1, "张三", null, null, 100, null);
        invoice.setId(1L);
        return invoice;
    }

    private Invoice issuedInvoice() {
        Invoice invoice = pendingInvoice();
        invoice.setStatus(InvoiceStatusEnum.ISSUED.getCode());
        return invoice;
    }

    @Test
    @DisplayName("AD57 分页 — 筛选参数透传仓储；urgeOnly=true 展开为催开排序视图")
    void shouldPassFilters_whenPage() {
        Page<Invoice> page = new Page<>(1, 20, 0);
        when(invoiceRepository.pageForAdmin(2, 200L, true, true, 2, 50)).thenReturn(page);

        AdminInvoicePageReq req = new AdminInvoicePageReq();
        req.setStatus(2);
        req.setSellerId(200L);
        req.setPartialRefund(true);
        req.setUrgeOnly(true);
        req.setPage(2);
        req.setSize(50);

        assertEquals(0, manager.page(req).getTotal());
        verify(invoiceRepository).pageForAdmin(2, 200L, true, true, 2, 50);
    }

    @Test
    @DisplayName("AD58 开具 — 先端口开具（票号+样票）后条件更新回填，再事务外触达（顺序不变量）")
    void shouldIssueThenPersistThenNotify_whenPending() {
        Invoice invoice = pendingInvoice();
        when(invoiceRepository.findByInvoiceNo(INVOICE_NO)).thenReturn(invoice);
        when(invoiceIssuePort.issue(any(InvoiceIssueParam.class)))
                .thenReturn(new InvoiceIssueResult(MOCK_INVOICE_NO, PDF_KEY));
        when(invoiceRepository.issueIfPending(eq(INVOICE_NO), eq(MOCK_INVOICE_NO),
                eq(PDF_KEY), any(LocalDateTime.class))).thenReturn(1);
        when(invoiceIssuePort.presignDownloadUrl(PDF_KEY)).thenReturn("https://minio/signed");

        InvoiceVO vo = manager.issue(INVOICE_NO);

        InOrder ordered = inOrder(invoiceIssuePort, invoiceRepository, tradeEventNotifier);
        ordered.verify(invoiceIssuePort).issue(any(InvoiceIssueParam.class));
        ordered.verify(invoiceRepository).issueIfPending(eq(INVOICE_NO), eq(MOCK_INVOICE_NO),
                eq(PDF_KEY), any(LocalDateTime.class));
        ordered.verify(tradeEventNotifier).notifyInvoiceIssued(invoice);
        ordered.verify(invoiceIssuePort).presignDownloadUrl(PDF_KEY);

        assertEquals(InvoiceStatusEnum.ISSUED.getCode(), invoice.getStatus());
        assertEquals(MOCK_INVOICE_NO, invoice.getMockInvoiceNo());
        assertEquals("https://minio/signed", vo.getPdfKey());
    }

    @Test
    @DisplayName("AD58 开具 — 非待开具拒绝且不触端口；条件更新 0 行（并发）→ 拒绝")
    void shouldRejectIssue_whenNotPendingOrRaceMiss() {
        when(invoiceRepository.findByInvoiceNo(INVOICE_NO)).thenReturn(issuedInvoice());
        assertThrows(BizException.class, () -> manager.issue(INVOICE_NO));
        verifyNoInteractions(invoiceIssuePort);

        when(invoiceRepository.findByInvoiceNo(INVOICE_NO)).thenReturn(pendingInvoice());
        when(invoiceIssuePort.issue(any(InvoiceIssueParam.class)))
                .thenReturn(new InvoiceIssueResult(MOCK_INVOICE_NO, PDF_KEY));
        when(invoiceRepository.issueIfPending(anyString(), anyString(), anyString(),
                any(LocalDateTime.class))).thenReturn(0);
        assertThrows(BizException.class, () -> manager.issue(INVOICE_NO));
        verify(tradeEventNotifier, never()).notifyInvoiceIssued(any());
    }

    @Test
    @DisplayName("AD59 作废 — 待开具命中并触达；已开具 → 合规守卫拒绝「请走冲红」")
    void shouldVoidPendingButRejectIssued() {
        when(invoiceRepository.findByInvoiceNo(INVOICE_NO)).thenReturn(pendingInvoice());
        when(invoiceRepository.cancelIfPending(INVOICE_NO, InvoiceAdminManager.REASON_ADMIN_VOID))
                .thenReturn(1);
        manager.voidInvoice(INVOICE_NO);
        verify(tradeEventNotifier).notifyInvoiceVoided(any(Invoice.class),
                eq(InvoiceAdminManager.REASON_ADMIN_VOID));

        String issuedInvoiceNo = "3f2a1b6c-0000-0000-0000-000000000002";
        when(invoiceRepository.findByInvoiceNo(issuedInvoiceNo)).thenReturn(issuedInvoice());
        BizException ex = assertThrows(BizException.class, () -> manager.voidInvoice(issuedInvoiceNo));
        assertTrue(ex.getMessage().contains("已开具发票不可作废"));
        assertTrue(ex.getMessage().contains("冲红"));
    }

    @Test
    @DisplayName("AD60 冲红 — 已开具条件迁移命中并触达；已冲红重复触发 = 幂等空操作")
    void shouldRedFlushIssuedAndIdempotent_whenAlreadyRed() {
        when(invoiceRepository.findByInvoiceNo(INVOICE_NO)).thenReturn(issuedInvoice());
        when(invoiceRepository.redFlushIfIssued(INVOICE_NO, "部分退款人工处理")).thenReturn(1);
        manager.redFlush(INVOICE_NO, "部分退款人工处理");
        verify(tradeEventNotifier).notifyInvoiceRedFlushed(any(Invoice.class), eq("部分退款人工处理"));

        Invoice redFlushed = issuedInvoice();
        redFlushed.setStatus(InvoiceStatusEnum.RED_FLUSHED.getCode());
        when(invoiceRepository.findByInvoiceNo(INVOICE_NO)).thenReturn(redFlushed);
        manager.redFlush(INVOICE_NO, "重复触发");
        verify(invoiceRepository, never()).redFlushIfIssued(INVOICE_NO, "重复触发");
        verify(tradeEventNotifier, never()).notifyInvoiceRedFlushed(any(Invoice.class), eq("重复触发"));
    }

    @Test
    @DisplayName("AD60 冲红 — 待开具/已作废拒绝（仅已开具可冲红）")
    void shouldRejectRedFlush_whenNotIssued() {
        when(invoiceRepository.findByInvoiceNo(INVOICE_NO)).thenReturn(pendingInvoice());
        BizException ex = assertThrows(BizException.class,
                () -> manager.redFlush(INVOICE_NO, "原因"));
        assertTrue(ex.getMessage().contains("仅已开具发票可冲红"));
        verify(invoiceRepository, never()).redFlushIfIssued(any(), any());
    }

    @Test
    @DisplayName("I56 全额退款 — 待开具 → 自动作废(4) + 站内信（系统原因码含退款幂等键）")
    void shouldAutoVoid_whenFullRefundOnPending() {
        when(invoiceRepository.voidOnFullRefund(ORDER_NO, InvoiceAdminManager.SYSTEM_REASON_FULL_REFUND_PREFIX
                + "GB:ORD-1:1")).thenReturn(4);
        when(invoiceRepository.findLatestByOrderNo(ORDER_NO)).thenReturn(pendingInvoice());

        manager.voidOnFullRefund(ORDER_NO, "GB:ORD-1:1");

        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(tradeEventNotifier).notifyInvoiceVoided(captor.capture(),
                eq(InvoiceAdminManager.SYSTEM_REASON_FULL_REFUND_PREFIX + "GB:ORD-1:1"));
        assertEquals(USER_ID, captor.getValue().getUserId());
    }

    @Test
    @DisplayName("I56 全额退款 — 已开具 → 自动冲红(3) + 站内信")
    void shouldAutoRedFlush_whenFullRefundOnIssued() {
        when(invoiceRepository.voidOnFullRefund(eq(ORDER_NO), anyString())).thenReturn(3);
        when(invoiceRepository.findLatestByOrderNo(ORDER_NO)).thenReturn(issuedInvoice());

        manager.voidOnFullRefund(ORDER_NO, "AS-9");

        verify(tradeEventNotifier).notifyInvoiceRedFlushed(any(Invoice.class),
                eq(InvoiceAdminManager.SYSTEM_REASON_FULL_REFUND_PREFIX + "AS-9"));
        verify(tradeEventNotifier, never()).notifyInvoiceVoided(any(), anyString());
    }

    @Test
    @DisplayName("I56 全额退款 — 无有效票/已终态 → 幂等空操作，不触达不查询")
    void shouldNoOp_whenNoActiveInvoice() {
        when(invoiceRepository.voidOnFullRefund(ORDER_NO, InvoiceAdminManager.SYSTEM_REASON_FULL_REFUND_PREFIX
                + "X")).thenReturn(0);

        manager.voidOnFullRefund(ORDER_NO, "X");

        verify(invoiceRepository, never()).findLatestByOrderNo(any());
        verifyNoInteractions(tradeEventNotifier);
    }

    @Test
    @DisplayName("I60 部分退款 — 已开具有效票置 partial_refund_flag=1（不改状态不触达）")
    void shouldMarkFlag_whenPartialRefundOnIssued() {
        when(invoiceRepository.markPartialRefundFlagIfIssued(ORDER_NO)).thenReturn(1);

        manager.markPartialRefundFlag(ORDER_NO, "PR:ORD-1:1");

        verify(invoiceRepository).markPartialRefundFlagIfIssued(ORDER_NO);
        verifyNoInteractions(tradeEventNotifier);
    }

    @Test
    @DisplayName("I60 部分退款 — 待开具/无票/终态/已置标（0 行）→ 幂等空操作，不触达")
    void shouldNoOp_whenPartialRefundNotOnIssued() {
        when(invoiceRepository.markPartialRefundFlagIfIssued(ORDER_NO)).thenReturn(0);

        manager.markPartialRefundFlag(ORDER_NO, "PR:ORD-1:2");

        verify(invoiceRepository).markPartialRefundFlagIfIssued(ORDER_NO);
        verifyNoInteractions(tradeEventNotifier);
    }

    @Test
    @DisplayName("通用 — 发票不存在 → NO_RESOURCE_FOUND")
    void shouldReject_whenInvoiceNotFound() {
        when(invoiceRepository.findByInvoiceNo(INVOICE_NO)).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> manager.issue(INVOICE_NO));
        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
    }
}
