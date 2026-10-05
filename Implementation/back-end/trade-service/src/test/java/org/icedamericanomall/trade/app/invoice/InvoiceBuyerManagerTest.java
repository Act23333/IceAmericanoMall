package org.icedamericanomall.trade.app.invoice;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.exception.ForbiddenException;
import org.icedamericanomall.common.utils.crypto.AesGcmUtil;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;
import org.icedamericanomall.trade.adapter.assembler.InvoiceAssembler;
import org.icedamericanomall.trade.adapter.request.InvoiceHistoryReq;
import org.icedamericanomall.trade.adapter.request.InvoiceTitleReq;
import org.icedamericanomall.trade.adapter.response.InvoiceTitleVO;
import org.icedamericanomall.trade.adapter.response.InvoiceVO;
import org.icedamericanomall.trade.domain.enums.InvoiceStatusEnum;
import org.icedamericanomall.trade.domain.model.invoice.Invoice;
import org.icedamericanomall.trade.domain.model.invoice.InvoiceTitle;
import org.icedamericanomall.trade.domain.port.InvoiceIssuePort;
import org.icedamericanomall.trade.domain.repository.InvoiceRepository;
import org.icedamericanomall.trade.domain.repository.InvoiceTitleRepository;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 买家发票用例单元测试（INV2-INV9，F14）：分页/详情（越权 + 样票地址）/
 * 撤回（条件迁移 0 行回落）/催开限频裁决映射/常用抬头 CRUD（默认清旧、归属校验、掩码出参）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InvoiceBuyerManager — INV2-INV5 查看撤回催开 + INV6-9 常用抬头编排")
class InvoiceBuyerManagerTest {

    private static final String INVOICE_NO = "3f2a1b6c-0000-0000-0000-000000000001";
    private static final String ORDER_NO = "ORD-1";
    private static final long USER_ID = 100L;
    private static final long OTHER_USER_ID = 999L;
    private static final long TITLE_ID = 5L;
    private static final String TAX_NO = "91110000123456789X";

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceTitleRepository invoiceTitleRepository;
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
        manager = new InvoiceBuyerManager(invoiceRepository, invoiceTitleRepository,
                invoiceIssuePort, new InvoiceAssembler());
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

    private InvoiceTitleReq personalReq() {
        InvoiceTitleReq req = new InvoiceTitleReq();
        req.setType(1);
        req.setTitle("张三");
        return req;
    }

    private InvoiceTitle savedTitle(long userId, Integer isDefault, String name, LocalDateTime createTime) {
        InvoiceTitle title = InvoiceTitle.create(userId, 2, name, TAX_NO, isDefault != null);
        title.setId(TITLE_ID);
        title.setCreateTime(createTime);
        return title;
    }

    // ==================== INV2-INV5（既有） ====================

    @Test
    @DisplayName("INV2 分页 — 仓储结果逐条转 VO，列表不解析样票地址")
    void shouldConvertPage_whenListMy() {
        Invoice invoice = pendingInvoice();
        Page<Invoice> page = new Page<>(1, 20, 1);
        page.setRecords(new ArrayList<>(List.of(invoice)));
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

    // ==================== INV6-9 常用抬头 ====================

    @Test
    @DisplayName("INV6 列表 — 默认在前，组内保持创建时间倒序；税号只出掩码、isDefault 归一 0/1")
    void shouldListDefaultFirst_whenListTitles() {
        // 仓储按创建时间倒序返回（新的在前）；默认抬头最旧 → 排序后应置顶
        InvoiceTitle newer = savedTitle(USER_ID, null, "新抬头", LocalDateTime.now().minusDays(1));
        InvoiceTitle oldestDefault = savedTitle(USER_ID, InvoiceTitle.DEFAULT_FLAG,
                "旧默认", LocalDateTime.now().minusDays(3));
        when(invoiceTitleRepository.listByUserId(USER_ID))
                .thenReturn(new ArrayList<>(List.of(newer, oldestDefault)));

        List<InvoiceTitleVO> vos = manager.listTitles(USER_ID);

        assertEquals(2, vos.size());
        assertEquals("旧默认", vos.get(0).getTitle());
        assertEquals(1, vos.get(0).getIsDefault());
        assertEquals("****789X", vos.get(0).getTaxNoMask(), "税号只出掩码");
        assertEquals("新抬头", vos.get(1).getTitle());
        assertEquals(0, vos.get(1).getIsDefault(), "NULL 归一为 0");
        assertEquals(2, vos.get(0).getType());
    }

    @Test
    @DisplayName("INV7 创建 — 个人抬头（无税号）→ 直接保存，不清旧默认")
    void shouldSaveWithoutClearing_whenCreatePersonal() {
        when(invoiceTitleRepository.save(any(InvoiceTitle.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InvoiceTitleVO vo = manager.createTitle(USER_ID, personalReq());

        assertNull(vo.getTaxNoMask());
        assertEquals(0, vo.getIsDefault());
        verify(invoiceTitleRepository, never()).clearDefaultFlag(any());
    }

    @Test
    @DisplayName("INV7 创建 — 企业缺税号 → PARAM_ERROR，不触仓储")
    void shouldRejectCreate_whenCompanyTaxNoMissing() {
        InvoiceTitleReq req = new InvoiceTitleReq();
        req.setType(2);
        req.setTitle("某公司");

        BizException ex = assertThrows(BizException.class, () -> manager.createTitle(USER_ID, req));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verifyNoInteractions(invoiceTitleRepository);
    }

    @Test
    @DisplayName("INV7 创建 — 设为默认 → 先清旧默认后保存（uk_user_default 语义，顺序不变量）")
    void shouldClearOldDefaultBeforeSave_whenCreateDefaultTitle() {
        InvoiceTitleReq req = personalReq();
        req.setIsDefault(true);
        when(invoiceTitleRepository.save(any(InvoiceTitle.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        manager.createTitle(USER_ID, req);

        InOrder ordered = inOrder(invoiceTitleRepository);
        ordered.verify(invoiceTitleRepository).clearDefaultFlag(USER_ID);
        ordered.verify(invoiceTitleRepository).save(any(InvoiceTitle.class));
    }

    @Test
    @DisplayName("INV8 编辑 — 非本人 → FORBIDDEN；不存在 → NO_RESOURCE_FOUND（归属校验先行）")
    void shouldRejectUpdate_whenNotOwnerOrMissing() {
        when(invoiceTitleRepository.findById(TITLE_ID)).thenReturn(null);
        BizException missing = assertThrows(BizException.class,
                () -> manager.updateTitle(USER_ID, TITLE_ID, personalReq()));
        assertEquals(ErrorCode.NO_RESOURCE_FOUND, missing.getErrorCode());

        when(invoiceTitleRepository.findById(TITLE_ID)).thenReturn(savedTitle(OTHER_USER_ID, null,
                "他人抬头", LocalDateTime.now()));
        assertThrows(ForbiddenException.class, () -> manager.updateTitle(USER_ID, TITLE_ID, personalReq()));
        verify(invoiceTitleRepository, never()).updateFull(any());
    }

    @Test
    @DisplayName("INV8 编辑 — 全量更新命中；非默认转默认先清旧默认")
    void shouldUpdateAndClearDefault_whenSwitchingToDefault() {
        InvoiceTitle existing = savedTitle(USER_ID, null, "旧抬头", LocalDateTime.now());
        when(invoiceTitleRepository.findById(TITLE_ID)).thenReturn(existing);
        when(invoiceTitleRepository.updateFull(any(InvoiceTitle.class))).thenReturn(1);

        InvoiceTitleReq req = personalReq();
        req.setTitle("张三改");
        req.setIsDefault(true);
        manager.updateTitle(USER_ID, TITLE_ID, req);

        ArgumentCaptor<InvoiceTitle> captor = ArgumentCaptor.forClass(InvoiceTitle.class);
        verify(invoiceTitleRepository).updateFull(captor.capture());
        assertEquals("张三改", captor.getValue().getTitle());
        assertTrue(captor.getValue().isDefaultTitle());
        InOrder ordered = inOrder(invoiceTitleRepository);
        ordered.verify(invoiceTitleRepository).clearDefaultFlag(USER_ID);
        ordered.verify(invoiceTitleRepository).updateFull(any(InvoiceTitle.class));
    }

    @Test
    @DisplayName("INV8 编辑 — 已是默认不重复清旧默认；并发已删（0 行）→ NO_RESOURCE_FOUND")
    void shouldSkipClear_whenAlreadyDefaultOrRaceDeleted() {
        InvoiceTitle existing = savedTitle(USER_ID, InvoiceTitle.DEFAULT_FLAG, "已默认", LocalDateTime.now());
        when(invoiceTitleRepository.findById(TITLE_ID)).thenReturn(existing);
        when(invoiceTitleRepository.updateFull(any(InvoiceTitle.class))).thenReturn(1);
        manager.updateTitle(USER_ID, TITLE_ID, personalReq());
        verify(invoiceTitleRepository, never()).clearDefaultFlag(any());

        when(invoiceTitleRepository.findById(TITLE_ID)).thenReturn(savedTitle(USER_ID, null,
                "再查抬头", LocalDateTime.now()));
        when(invoiceTitleRepository.updateFull(any(InvoiceTitle.class))).thenReturn(0);
        assertThrows(BizException.class, () -> manager.updateTitle(USER_ID, TITLE_ID, personalReq()));
    }

    @Test
    @DisplayName("INV9 删除 — 本人抬头软删；默认抬头删除不自动递补（无清标记/无插入）")
    void shouldSoftDeleteWithoutRecruit_whenDeleteOwnedTitle() {
        InvoiceTitle defaultTitle = savedTitle(USER_ID, InvoiceTitle.DEFAULT_FLAG, "默认抬头",
                LocalDateTime.now());
        when(invoiceTitleRepository.findById(TITLE_ID)).thenReturn(defaultTitle);

        manager.deleteTitle(USER_ID, TITLE_ID);

        verify(invoiceTitleRepository).deleteByIdAndUserId(TITLE_ID, USER_ID);
        verify(invoiceTitleRepository, never()).clearDefaultFlag(any());
        verify(invoiceTitleRepository, never()).save(any());
    }

    @Test
    @DisplayName("INV9 删除 — 不存在 → NO_RESOURCE_FOUND；非本人 → FORBIDDEN")
    void shouldRejectDelete_whenMissingOrNotOwner() {
        when(invoiceTitleRepository.findById(TITLE_ID)).thenReturn(null);
        BizException missing = assertThrows(BizException.class,
                () -> manager.deleteTitle(USER_ID, TITLE_ID));
        assertEquals(ErrorCode.NO_RESOURCE_FOUND, missing.getErrorCode());

        when(invoiceTitleRepository.findById(TITLE_ID)).thenReturn(savedTitle(OTHER_USER_ID, null,
                "他人抬头", LocalDateTime.now()));
        assertThrows(ForbiddenException.class, () -> manager.deleteTitle(USER_ID, TITLE_ID));
        verify(invoiceTitleRepository, never()).deleteByIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("INV1 titleId 引用链路回归 — 创建的企业抬头要素（类型/抬头/税号明文）可被申请快照消费")
    void shouldExposeSnapshotFields_whenCompanyTitleCreated() {
        InvoiceTitleReq req = new InvoiceTitleReq();
        req.setType(2);
        req.setTitle("某公司");
        req.setTaxNo(TAX_NO);
        when(invoiceTitleRepository.save(any(InvoiceTitle.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        manager.createTitle(USER_ID, req);

        ArgumentCaptor<InvoiceTitle> captor = ArgumentCaptor.forClass(InvoiceTitle.class);
        verify(invoiceTitleRepository).save(captor.capture());
        InvoiceTitle saved = captor.getValue();
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(2, saved.getType());
        assertEquals("某公司", saved.getTitle());
        assertEquals(TAX_NO, saved.getTaxNo().getPlain(),
                "INV1 resolveTitleSnapshot 依赖 getPlain() 取抬头快照");
    }
}
