package org.icedamericanomall.trade.infrastructure.persistence.invoice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.model.EncryptedIdentifier;
import org.icedamericanomall.trade.domain.model.invoice.Invoice;
import org.icedamericanomall.trade.domain.repository.InvoiceRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 发票单仓储实现 —— PO 与聚合互转在本类内联完成（对齐 {@code AfterSaleRepositoryImpl} 先例）。
 *
 * <p>条件迁移 SQL 语义在 {@link InvoiceMapper} 契约化（显式 SQL，WHERE 带期望状态）；
 * {@link #voidOnFullRefund} 含两条条件更新，属多语句本地写，事务下沉到本实现
 * （硬约束：Feign 严禁进事务——本方法纯本地，无跨服务调用）。
 */
@Repository
@RequiredArgsConstructor
public class InvoiceRepositoryImpl implements InvoiceRepository {

    private final InvoiceMapper invoiceMapper;

    @Override
    public Invoice save(Invoice invoice) {
        InvoicePO po = toPO(invoice);
        invoiceMapper.insert(po);
        invoice.setId(po.getId());
        return invoice;
    }

    @Override
    public Invoice findByInvoiceNo(String invoiceNo) {
        return toDomain(invoiceMapper.selectOne(new LambdaQueryWrapper<InvoicePO>()
                .eq(InvoicePO::getInvoiceNo, invoiceNo)));
    }

    @Override
    public Invoice findLatestByOrderNo(String orderNo) {
        var list = invoiceMapper.selectList(new LambdaQueryWrapper<InvoicePO>()
                .eq(InvoicePO::getOrderNo, orderNo)
                .orderByDesc(InvoicePO::getId));
        return list.isEmpty() ? null : toDomain(list.get(0));
    }

    @Override
    public Invoice findActiveByOrderNo(String orderNo) {
        return toDomain(invoiceMapper.selectOne(new LambdaQueryWrapper<InvoicePO>()
                .eq(InvoicePO::getOrderNo, orderNo)
                .eq(InvoicePO::getActiveFlag, 1)));
    }

    @Override
    public IPage<Invoice> pageByBuyer(Long userId, Integer status, int page, int size) {
        var wrapper = new LambdaQueryWrapper<InvoicePO>()
                .eq(InvoicePO::getUserId, userId)
                .orderByDesc(InvoicePO::getCreateTime);
        if (status != null) {
            wrapper.eq(InvoicePO::getStatus, status);
        }
        return invoiceMapper.selectPage(new Page<>(page, size), wrapper)
                .convert(InvoiceRepositoryImpl::toDomain);
    }

    @Override
    public IPage<Invoice> pageForAdmin(Integer status, Long sellerId, Boolean partialRefund,
                                       boolean urgeOnly, int page, int size) {
        var wrapper = new LambdaQueryWrapper<InvoicePO>();
        if (status != null) {
            wrapper.eq(InvoicePO::getStatus, status);
        }
        if (sellerId != null) {
            wrapper.eq(InvoicePO::getSellerId, sellerId);
        }
        if (partialRefund != null) {
            wrapper.eq(InvoicePO::getPartialRefundFlag, partialRefund ? 1 : 0);
        }
        if (urgeOnly) {
            // O10 催开排序视图：仅看有催开记录的发票，按最近催开时间倒序
            wrapper.isNotNull(InvoicePO::getUrgeTime).orderByDesc(InvoicePO::getUrgeTime);
        } else {
            wrapper.orderByDesc(InvoicePO::getCreateTime);
        }
        return invoiceMapper.selectPage(new Page<>(page, size), wrapper)
                .convert(InvoiceRepositoryImpl::toDomain);
    }

    @Override
    public int issueIfPending(String invoiceNo, String mockInvoiceNo, String pdfKey,
                              LocalDateTime issueTime) {
        return invoiceMapper.issueIfPending(invoiceNo, mockInvoiceNo, pdfKey, issueTime);
    }

    @Override
    public int cancelIfPending(String invoiceNo, String reason) {
        return invoiceMapper.cancelIfPending(invoiceNo, reason);
    }

    @Override
    public int redFlushIfIssued(String invoiceNo, String reason) {
        return invoiceMapper.redFlushIfIssued(invoiceNo, reason);
    }

    @Override
    public int markRedAndExpire(String invoiceNo, String reason) {
        // 换开前置冲红与人工冲红共用 2→3 条件迁移边（冲红语义相同，差异只在触发方与原因留痕）
        return invoiceMapper.redFlushIfIssued(invoiceNo, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int voidOnFullRefund(String orderNo, String systemReason) {
        // 一单一有效票（uk_order_active）：两条条件更新至多一条命中，结果互斥
        int voided = invoiceMapper.voidPendingByOrderNo(orderNo, systemReason);
        if (voided > 0) {
            return 4;
        }
        int redFlushed = invoiceMapper.redFlushIssuedByOrderNo(orderNo, systemReason);
        return redFlushed > 0 ? 3 : 0;
    }

    @Override
    public int markUrged(String invoiceNo, LocalDateTime urgeTime) {
        return invoiceMapper.markUrged(invoiceNo, urgeTime);
    }

    // ==================== PO <-> 聚合 ====================

    static Invoice toDomain(InvoicePO po) {
        if (po == null) {
            return null;
        }
        Invoice invoice = new Invoice();
        invoice.setId(po.getId());
        invoice.setInvoiceNo(po.getInvoiceNo());
        invoice.setOrderNo(po.getOrderNo());
        invoice.setActiveFlag(po.getActiveFlag());
        invoice.setUserId(po.getUserId());
        invoice.setSellerId(po.getSellerId());
        invoice.setType(po.getType());
        invoice.setTitle(po.getTitle());
        invoice.setTaxNo(toIdentifier(po.getTaxNoCipher(), po.getTaxNoHash(), po.getTaxNoMask()));
        invoice.setEmail(toIdentifier(po.getEmailCipher(), po.getEmailHash(), po.getEmailMask()));
        invoice.setAmount(po.getAmount());
        invoice.setStatus(po.getStatus());
        invoice.setIssueTime(po.getIssueTime());
        invoice.setMockInvoiceNo(po.getMockInvoiceNo());
        invoice.setPdfKey(po.getPdfKey());
        invoice.setRedForId(po.getRedForId());
        invoice.setRedReason(po.getRedReason());
        invoice.setPartialRefundFlag(po.getPartialRefundFlag());
        invoice.setUrgeTime(po.getUrgeTime());
        invoice.setCreateTime(po.getCreateTime());
        invoice.setUpdateTime(po.getUpdateTime());
        return invoice;
    }

    static InvoicePO toPO(Invoice invoice) {
        InvoicePO po = new InvoicePO();
        po.setId(invoice.getId());
        po.setInvoiceNo(invoice.getInvoiceNo());
        po.setOrderNo(invoice.getOrderNo());
        po.setActiveFlag(invoice.getActiveFlag());
        po.setUserId(invoice.getUserId());
        po.setSellerId(invoice.getSellerId());
        po.setType(invoice.getType());
        po.setTitle(invoice.getTitle());
        if (invoice.getTaxNo() != null) {
            po.setTaxNoCipher(invoice.getTaxNo().getCipher());
            po.setTaxNoHash(invoice.getTaxNo().getHash());
            po.setTaxNoMask(invoice.getTaxNo().getMask());
        }
        if (invoice.getEmail() != null) {
            po.setEmailCipher(invoice.getEmail().getCipher());
            po.setEmailHash(invoice.getEmail().getHash());
            po.setEmailMask(invoice.getEmail().getMask());
        }
        po.setAmount(invoice.getAmount());
        po.setStatus(invoice.getStatus());
        po.setIssueTime(invoice.getIssueTime());
        po.setMockInvoiceNo(invoice.getMockInvoiceNo());
        po.setPdfKey(invoice.getPdfKey());
        po.setRedForId(invoice.getRedForId());
        po.setRedReason(invoice.getRedReason());
        po.setPartialRefundFlag(invoice.getPartialRefundFlag());
        po.setUrgeTime(invoice.getUrgeTime());
        return po;
    }

    /** 三字段列 → {@link EncryptedIdentifier}（三列全空视为未填写）。 */
    private static EncryptedIdentifier toIdentifier(String cipher, String hash, String mask) {
        if (cipher == null && hash == null && mask == null) {
            return null;
        }
        return EncryptedIdentifier.fromStorage(cipher, hash, mask);
    }
}
