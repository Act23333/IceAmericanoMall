package org.icedamericanomall.trade.app.invoice;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.exception.ForbiddenException;
import org.icedamericanomall.common.utils.crypto.AesGcmUtil;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;
import org.icedamericanomall.trade.adapter.assembler.InvoiceAssembler;
import org.icedamericanomall.trade.adapter.request.InvoiceApplyReq;
import org.icedamericanomall.trade.adapter.response.InvoiceVO;
import org.icedamericanomall.trade.domain.enums.InvoiceStatusEnum;
import org.icedamericanomall.trade.domain.enums.OrderStatusEnum;
import org.icedamericanomall.trade.domain.model.invoice.Invoice;
import org.icedamericanomall.trade.domain.model.invoice.InvoiceTitle;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.repository.InvoiceRepository;
import org.icedamericanomall.trade.domain.repository.InvoiceTitleRepository;
import org.icedamericanomall.trade.domain.repository.OrderRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发票申请用例守卫矩阵单元测试（INV1，F14；契约 §4.15 申请守卫，ADR-004）。
 *
 * <p>覆盖：订单存在/归属（越权 ForbiddenException 由仓储端口抛出）/仅已完成/90 天窗/
 * 一单一有效票预检与 {@code uk_order_active} DuplicateKey 回落/换开 = 冲红重开/
 * titleId 引用常用抬头/金额 = payAmount 快照断言。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InvoiceApplyManager — INV1 申请守卫矩阵与换开语义")
class InvoiceApplyManagerTest {

    private static final String ORDER_NO = "ORD-20261005-001";
    private static final long USER_ID = 100L;
    private static final long SELLER_ID = 200L;
    /** 实付 = 12000 − 330 + 880 = 12550 分（不变式 V2）。 */
    private static final int PAY_AMOUNT = 12550;

    @Mock private OrderRepository orderRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceTitleRepository invoiceTitleRepository;

    private InvoiceApplyManager manager;

    @BeforeAll
    static void initCrypto() {
        AesGcmUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
        BlindIndexUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
    }

    @BeforeEach
    void setUp() {
        manager = new InvoiceApplyManager(orderRepository, invoiceRepository,
                invoiceTitleRepository, new InvoiceAssembler());
    }

    private Order completedOrder() {
        Order order = new Order();
        order.setOrderNo(ORDER_NO);
        order.setUserId(USER_ID);
        order.setSellerId(SELLER_ID);
        order.setStatus(OrderStatusEnum.COMPLETED.getCode());
        order.setTotalAmount(12000);
        order.setDiscountAmount(330);
        order.setPostFee(880);
        order.setPayAmount(PAY_AMOUNT);
        order.setEndTime(LocalDateTime.now().minusDays(1));
        return order;
    }

    private InvoiceApplyReq personalReq() {
        InvoiceApplyReq req = new InvoiceApplyReq();
        req.setOrderNo(ORDER_NO);
        req.setType(1);
        req.setTitle("张三");
        req.setEmail("buyer@example.com");
        return req;
    }

    @Test
    @DisplayName("申请成功 — 金额 = payAmount 快照（12550），状态待开具，VO 脱敏出邮箱掩码")
    void shouldSnapshotPayAmount_whenApplySucceeds() {
        when(orderRepository.findOwnedOrder(ORDER_NO, USER_ID)).thenReturn(completedOrder());
        when(invoiceRepository.findActiveByOrderNo(ORDER_NO)).thenReturn(null);
        when(invoiceRepository.findLatestByOrderNo(ORDER_NO)).thenReturn(null);
        when(invoiceRepository.save(any(Invoice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Invoice.class));

        InvoiceVO vo = manager.apply(USER_ID, personalReq());

        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(captor.capture());
        Invoice saved = captor.getValue();
        assertEquals(PAY_AMOUNT, saved.getAmount(), "开票金额必须 = payAmount 快照（含运费扣优惠）");
        assertEquals(InvoiceStatusEnum.PENDING_ISSUE.getCode(), saved.getStatus());
        assertEquals(1, saved.getActiveFlag());
        assertEquals(SELLER_ID, saved.getSellerId());
        assertEquals("张三", vo.getTitle());
        assertEquals(PAY_AMOUNT, vo.getAmount());
        assertNotNull(vo.getEmailMask(), "VO 邮箱必须脱敏展示");
        assertTrue(vo.getEmailMask().contains("*"));
    }

    @Test
    @DisplayName("订单非本人 — 仓储端口抛 ForbiddenException 原样上抛（越权红线）")
    void shouldPropagateForbidden_whenOrderNotOwned() {
        when(orderRepository.findOwnedOrder(ORDER_NO, USER_ID))
                .thenThrow(new ForbiddenException(ErrorCode.FORBIDDEN, "无权查看该订单"));

        assertThrows(ForbiddenException.class, () -> manager.apply(USER_ID, personalReq()));
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("非已完成订单 → 拒绝（仅已完成可申请）")
    void shouldReject_whenOrderNotCompleted() {
        Order order = completedOrder();
        order.setStatus(OrderStatusEnum.PENDING_RECEIPT.getCode());
        when(orderRepository.findOwnedOrder(ORDER_NO, USER_ID)).thenReturn(order);

        BizException ex = assertThrows(BizException.class, () -> manager.apply(USER_ID, personalReq()));
        assertTrue(ex.getMessage().contains("订单状态不支持开票"));
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("超出 90 天窗 → 拒绝（管理端仍可补开）")
    void shouldReject_whenBeyondApplyWindow() {
        Order order = completedOrder();
        order.setEndTime(LocalDateTime.now().minusDays(91));
        when(orderRepository.findOwnedOrder(ORDER_NO, USER_ID)).thenReturn(order);

        BizException ex = assertThrows(BizException.class, () -> manager.apply(USER_ID, personalReq()));
        assertTrue(ex.getMessage().contains("已超出开票时限"));
    }

    @Test
    @DisplayName("金额口径被破坏 → 拒绝开票（防虚开防御）")
    void shouldReject_whenAmountInvariantBroken() {
        Order order = completedOrder();
        order.setPayAmount(12000);
        when(orderRepository.findOwnedOrder(ORDER_NO, USER_ID)).thenReturn(order);

        assertThrows(BizException.class, () -> manager.apply(USER_ID, personalReq()));
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("重复申请 — 已存在待开具有效票 → 拒绝，不再落库")
    void shouldReject_whenActivePendingExists() {
        when(orderRepository.findOwnedOrder(ORDER_NO, USER_ID)).thenReturn(completedOrder());
        Invoice active = Invoice.create(ORDER_NO, USER_ID, SELLER_ID, 1, "张三", null, null, PAY_AMOUNT, null);
        when(invoiceRepository.findActiveByOrderNo(ORDER_NO)).thenReturn(active);

        BizException ex = assertThrows(BizException.class, () -> manager.apply(USER_ID, personalReq()));
        assertTrue(ex.getMessage().contains("该订单已申请过发票"));
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("换开 = 冲红重开 — 已开具有效票：原票条件冲红 + 新票 redForId 关联原票")
    void shouldRedFlushAndRelink_whenReapplyOnIssued() {
        when(orderRepository.findOwnedOrder(ORDER_NO, USER_ID)).thenReturn(completedOrder());
        Invoice issued = Invoice.create(ORDER_NO, USER_ID, SELLER_ID, 1, "张三", null, null, PAY_AMOUNT, null);
        issued.setId(9L);
        issued.setStatus(InvoiceStatusEnum.ISSUED.getCode());
        when(invoiceRepository.findActiveByOrderNo(ORDER_NO)).thenReturn(issued);
        when(invoiceRepository.markRedAndExpire(eq(issued.getInvoiceNo()), any())).thenReturn(1);
        when(invoiceRepository.save(any(Invoice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Invoice.class));

        InvoiceApplyReq companyReq = new InvoiceApplyReq();
        companyReq.setOrderNo(ORDER_NO);
        companyReq.setType(2);
        companyReq.setTitle("某某公司");
        companyReq.setTaxNo("91110000123456789X");

        manager.apply(USER_ID, companyReq);

        verify(invoiceRepository).markRedAndExpire(eq(issued.getInvoiceNo()),
                eq(InvoiceApplyManager.REASON_EXCHANGE));
        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(captor.capture());
        assertEquals(9L, captor.getValue().getRedForId(), "新票必须关联被冲红原票");
        assertEquals(2, captor.getValue().getType(), "换开新票抬头以新申请为准");
        assertNotNull(captor.getValue().getTaxNo());
    }

    @Test
    @DisplayName("换开竞态 — 原票条件冲红 0 行（已被并发处理）→ 按预检口径拒绝")
    void shouldReject_whenExchangeRedFlushMisses() {
        when(orderRepository.findOwnedOrder(ORDER_NO, USER_ID)).thenReturn(completedOrder());
        Invoice issued = Invoice.create(ORDER_NO, USER_ID, SELLER_ID, 1, "张三", null, null, PAY_AMOUNT, null);
        issued.setStatus(InvoiceStatusEnum.ISSUED.getCode());
        when(invoiceRepository.findActiveByOrderNo(ORDER_NO)).thenReturn(issued);
        when(invoiceRepository.markRedAndExpire(any(), any())).thenReturn(0);

        assertThrows(BizException.class, () -> manager.apply(USER_ID, personalReq()));
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("并发兜底 — 落库撞 uk_order_active 唯一键 → DuplicateKey 回落拒绝")
    void shouldRejectOnDuplicateKey_whenConcurrentInsert() {
        when(orderRepository.findOwnedOrder(ORDER_NO, USER_ID)).thenReturn(completedOrder());
        when(invoiceRepository.findActiveByOrderNo(ORDER_NO)).thenReturn(null);
        when(invoiceRepository.findLatestByOrderNo(ORDER_NO)).thenReturn(null);
        when(invoiceRepository.save(any(Invoice.class)))
                .thenThrow(new DuplicateKeyException("uk_order_active"));

        BizException ex = assertThrows(BizException.class, () -> manager.apply(USER_ID, personalReq()));
        assertTrue(ex.getMessage().contains("该订单已申请过发票"));
    }

    @Test
    @DisplayName("titleId 引用 — 类型/抬头/税号以常用抬头快照为准，报文现填字段忽略")
    void shouldSnapshotFromTitle_whenTitleIdReferenced() {
        when(orderRepository.findOwnedOrder(ORDER_NO, USER_ID)).thenReturn(completedOrder());
        when(invoiceRepository.findActiveByOrderNo(ORDER_NO)).thenReturn(null);
        when(invoiceRepository.findLatestByOrderNo(ORDER_NO)).thenReturn(null);
        when(invoiceRepository.save(any(Invoice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Invoice.class));

        InvoiceTitle title = new InvoiceTitle();
        title.setId(5L);
        title.setUserId(USER_ID);
        title.setType(2);
        title.setTitle("引用抬头公司");
        title.setTaxNo(org.icedamericanomall.common.model.EncryptedIdentifier.fromPlain("91110000123456789X"));
        when(invoiceTitleRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(title);

        InvoiceApplyReq req = personalReq();
        req.setTitleId(5L);
        manager.apply(USER_ID, req);

        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(captor.capture());
        assertEquals("引用抬头公司", captor.getValue().getTitle(), "抬头以引用抬头为准");
        assertEquals(2, captor.getValue().getType());
        assertNotNull(captor.getValue().getTaxNo().getMask());
    }

    @Test
    @DisplayName("titleId 引用 — 抬头不存在或非本人 → NO_RESOURCE_FOUND")
    void shouldReject_whenTitleNotOwned() {
        when(orderRepository.findOwnedOrder(ORDER_NO, USER_ID)).thenReturn(completedOrder());
        when(invoiceTitleRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(null);

        InvoiceApplyReq req = personalReq();
        req.setTitleId(5L);
        BizException ex = assertThrows(BizException.class, () -> manager.apply(USER_ID, req));
        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
    }
}
