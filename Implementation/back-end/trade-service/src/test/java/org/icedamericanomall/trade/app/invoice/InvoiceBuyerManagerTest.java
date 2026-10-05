package org.icedamericanomall.trade.app.invoice;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.exception.ForbiddenException;
import org.icedamericanomall.common.utils.crypto.AesGcmUtil;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;
import org.icedamericanomall.trade.adapter.assembler.InvoiceAssembler;
import org.icedamericanomall.trade.adapter.request.InvoiceHistoryReq;
import org.icedamericanomall.trade.adapter.response.InvoiceVO;
import org.icedamericanomall.trade.domain.enums.InvoiceStatusEnum;
import org.icedamericanomall.trade.domain.model.invoice.Invoice;
import org.icedamericanomall.trade.domain.port.InvoiceIssuePort;
import org.icedamericanomall.trade.domain.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 买家发票用例单元测试（INV2-INV5，F14）：分页/详情（越权 + 样票地址）/
 * 撤回（条件迁移 0 行回落）/催开限频裁决映射。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InvoiceBuyerManager — INV2-INV5 查看撤回催开编排")
class InvoiceBuyerManagerTest {

    private static final String INVOICE_NO = "3f2a1b6c-0000-0000-0000-000000000001";
    private static final String ORDER_NO = "ORD-1";
    private static final long USER_ID = 100L;
    private static final long OTHER_USER_ID = 999L;

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceIssuePort invoiceIssuePort;

    private InvoiceBuyerManager manager;

    @BeforeAll
    static void initCrypto() {
        // Invoice.create 自封装会对邮箱执行 AES-GCM 加密，单测预置密钥（authorization 先例）
        AesGcmUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
        BlindIndexUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
    }

    @BeforeEach
    void setUp() {
        manager = new InvoiceBuyerManager(invoiceRepository, invoiceIssuePort, new InvoiceAssembler());
    }

    private Invoice pendingInvoice() {
        Invoice invoice = Invoice.create(ORDER_NO, USER_ID, 200L, 1, "张三", null, "b@e.com", 100, null);
        invoice.setId(1L);
        invoice.setInvoiceNo(INVOICE_NO);
        return invoice;
    }

    private Invoice issuedInvoice() {
        Invoice invoice = pendingInvoice();
        invoice.setStatus(InvoiceStatusEnum.ISSUED.getCode());
        invoice.setPdfKey("invoice/" + INVOICE_NO + ".pdf");
        invoice.setMockInvoiceNo("MOCK-ABCDEF12");
        return invoice;
    }

    @Test
    @DisplayName("INV2 分页 — 仓储结果逐条转 VO，列表不解析样票地址")
    void shouldConvertPage_whenListMy() {
        Invoice invoice = pendingInvoice();
        Page<Invoice> page = new Page<>(1, 20, 1);
        page.setRecords(new java.util.ArrayList<>(java.util.List.of(invoice)));
        when(invoiceRepository.pageByBuyer(USER_ID, null, 1, 20)).thenReturn(page);

        var result = manager.pageMy(USER_ID, new InvoiceHistoryReq());

        assertEquals(1, result.getTotal());
        assertEquals(INVOICE_NO, result.getRecords().get(0).getInvoiceNo());
        assertNull(result.getRecords().get(0).getPdfKey());
        verifyNoInteractions(invoiceIssuePort);
    }

    @Test
    @DisplayName("INV3 详情 — 本人已开具发票解析样票下载地址；终态不解析")
    void shouldPresignOnlyIssued_whenDetail() {
        Invoice issued = issuedInvoice();
        when(invoiceRepository.findByInvoiceNo(INVOICE_NO)).thenReturn(issued);
        when(invoiceIssuePort.presignDownloadUrl(issued.getPdfKey())).thenReturn("https://minio/signed-url");

        InvoiceVO vo = manager.detail(USER_ID, INVOICE_NO);

        assertEquals("https://minio/signed-url", vo.getPdfKey());
        assertEquals(INVOICE_NO, vo.getInvoiceNo());
        assertTrue(vo.getTaxNoMask() == null && vo.getEmailMask().contains("*"));
    }

    @Test
    @DisplayName("INV3 详情 — 发票不存在 → NO_RESOURCE_FOUND")
    void shouldReject_whenInvoiceNotFound() {
        when(invoiceRepository.findByInvoiceNo(INVOICE_NO)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> manager.detail(USER_ID, INVOICE_NO));
        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("INV3 详情 — 非本人发票 → FORBIDDEN（越权防护，PRD §15.5.3）")
    void shouldReject_whenNotOwner() {
        when(invoiceRepository.findByInvoiceNo(INVOICE_NO)).thenReturn(pendingInvoice());

        assertThrows(ForbiddenException.class, () -> manager.detail(OTHER_USER_ID, INVOICE_NO));
        verifyNoInteractions(invoiceIssuePort);
    }

    @Test
    @DisplayName("INV4 撤回 — 待开具条件迁移命中（原因 null = 买家撤回无留痕）")
    void shouldCancel_whenPending() {
        when(invoiceRepository.findByInvoiceNo(INVOICE_NO)).thenReturn(pendingInvoice());
        when(invoiceRepository.cancelIfPending(INVOICE_NO, null)).thenReturn(1);

        manager.cancel(USER_ID, INVOICE_NO);

        verify(invoiceRepository).cancelIfPending(INVOICE_NO, null);
    }

    @Test
    @DisplayName("INV4 撤回 — 已开具/终态拒绝；条件更新 0 行（并发）回落拒绝")
    void shouldRejectCancel_whenNotPendingOrRaceMiss() {
        when(invoiceRepository.findByInvoiceNo(INVOICE_NO)).thenReturn(issuedInvoice());
        assertThrows(BizException.class, () -> manager.cancel(USER_ID, INVOICE_NO));
        verify(invoiceRepository, never()).cancelIfPending(any(), any());

        when(invoiceRepository.findByInvoiceNo(INVOICE_NO)).thenReturn(pendingInvoice());
        when(invoiceRepository.cancelIfPending(INVOICE_NO, null)).thenReturn(0);
        assertThrows(BizException.class, () -> manager.cancel(USER_ID, INVOICE_NO));
    }

    @Test
    @DisplayName("INV5 催开 — 满 7 天未催开 → 记录 urge_time（O10）")
    void shouldMarkUrged_whenUrgeAllowed() {
        Invoice invoice = pendingInvoice();
        invoice.setCreateTime(LocalDateTime.now().minusDays(8));
        when(invoiceRepository.findByInvoiceNo(INVOICE_NO)).thenReturn(invoice);
        when(invoiceRepository.markUrged(eq(INVOICE_NO), any(LocalDateTime.class))).thenReturn(1);

        manager.urge(USER_ID, INVOICE_NO);

        verify(invoiceRepository).markUrged(eq(INVOICE_NO), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("INV5 催开 — 24h 内已催开 → FREQUENT_ERROR 限频")
    void shouldRateLimit_whenUrgedWithin24h() {
        Invoice invoice = pendingInvoice();
        invoice.setCreateTime(LocalDateTime.now().minusDays(8));
        invoice.setUrgeTime(LocalDateTime.now().minusHours(1));
        when(invoiceRepository.findByInvoiceNo(INVOICE_NO)).thenReturn(invoice);

        BizException ex = assertThrows(BizException.class, () -> manager.urge(USER_ID, INVOICE_NO));
        assertEquals(ErrorCode.FREQUENT_ERROR, ex.getErrorCode());
        verify(invoiceRepository, never()).markUrged(any(), any());
    }

    @Test
    @DisplayName("INV5 催开 — 未满 7 天 / 已开具拒绝；命中条件更新 0 行回落拒绝")
    void shouldRejectUrge_whenTooEarlyOrNotPendingOrRace() {
        Invoice fresh = pendingInvoice();
        fresh.setCreateTime(LocalDateTime.now().minusDays(1));
        when(invoiceRepository.findByInvoiceNo(INVOICE_NO)).thenReturn(fresh);
        assertThrows(BizException.class, () -> manager.urge(USER_ID, INVOICE_NO));

        Invoice issued = issuedInvoice();
        issued.setCreateTime(LocalDateTime.now().minusDays(8));
        when(invoiceRepository.findByInvoiceNo(INVOICE_NO)).thenReturn(issued);
        assertThrows(BizException.class, () -> manager.urge(USER_ID, INVOICE_NO));

        Invoice allowed = pendingInvoice();
        allowed.setCreateTime(LocalDateTime.now().minusDays(8));
        when(invoiceRepository.findByInvoiceNo(INVOICE_NO)).thenReturn(allowed);
        when(invoiceRepository.markUrged(eq(INVOICE_NO), any(LocalDateTime.class))).thenReturn(0);
        assertThrows(BizException.class, () -> manager.urge(USER_ID, INVOICE_NO));

        verify(invoiceRepository, never()).markUrged(any(), isNull());
    }
}
