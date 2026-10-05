package org.icedamericanomall.trade.infrastructure.persistence.invoice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.model.EncryptedIdentifier;
import org.icedamericanomall.trade.domain.model.invoice.InvoiceTitle;
import org.icedamericanomall.trade.domain.repository.InvoiceTitleRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 常用发票抬头仓储实现 —— PO 与聚合互转在本类内联完成；
 * 软删由 {@code InvoiceTitlePO.deleted} 的 {@code @TableLogic} 承载（MP 自动过滤已删行）。
 */
@Repository
@RequiredArgsConstructor
public class InvoiceTitleRepositoryImpl implements InvoiceTitleRepository {

    private final InvoiceTitleMapper invoiceTitleMapper;

    @Override
    public InvoiceTitle findById(Long id) {
        return toDomain(invoiceTitleMapper.selectById(id));
    }

    @Override
    public InvoiceTitle findByIdAndUserId(Long id, Long userId) {
        return toDomain(invoiceTitleMapper.selectOne(new LambdaQueryWrapper<InvoiceTitlePO>()
                .eq(InvoiceTitlePO::getId, id)
                .eq(InvoiceTitlePO::getUserId, userId)));
    }

    @Override
    public InvoiceTitle save(InvoiceTitle title) {
        InvoiceTitlePO po = toPO(title);
        invoiceTitleMapper.insert(po);
        title.setId(po.getId());
        return title;
    }

    @Override
    public void update(InvoiceTitle title) {
        invoiceTitleMapper.updateById(toPO(title));
    }

    @Override
    public int updateFull(InvoiceTitle title) {
        return invoiceTitleMapper.updateTitleById(toPO(title));
    }

    @Override
    public void deleteByIdAndUserId(Long id, Long userId) {
        invoiceTitleMapper.delete(new LambdaQueryWrapper<InvoiceTitlePO>()
                .eq(InvoiceTitlePO::getId, id)
                .eq(InvoiceTitlePO::getUserId, userId));
    }

    @Override
    public List<InvoiceTitle> listByUserId(Long userId) {
        return invoiceTitleMapper.selectList(new LambdaQueryWrapper<InvoiceTitlePO>()
                        .eq(InvoiceTitlePO::getUserId, userId)
                        .orderByDesc(InvoiceTitlePO::getCreateTime))
                .stream().map(InvoiceTitleRepositoryImpl::toDomain).toList();
    }

    @Override
    public int clearDefaultFlag(Long userId) {
        return invoiceTitleMapper.clearDefaultByUserId(userId);
    }

    // ==================== PO <-> 聚合 ====================

    static InvoiceTitle toDomain(InvoiceTitlePO po) {
        if (po == null) {
            return null;
        }
        InvoiceTitle title = new InvoiceTitle();
        title.setId(po.getId());
        title.setUserId(po.getUserId());
        title.setType(po.getType());
        title.setTitle(po.getTitle());
        title.setTaxNo(toIdentifier(po.getTaxNoCipher(), po.getTaxNoHash(), po.getTaxNoMask()));
        title.setIsDefault(po.getIsDefault());
        title.setDeleted(po.getDeleted());
        title.setCreateTime(po.getCreateTime());
        title.setUpdateTime(po.getUpdateTime());
        return title;
    }

    static InvoiceTitlePO toPO(InvoiceTitle title) {
        InvoiceTitlePO po = new InvoiceTitlePO();
        po.setId(title.getId());
        po.setUserId(title.getUserId());
        po.setType(title.getType());
        po.setTitle(title.getTitle());
        if (title.getTaxNo() != null) {
            po.setTaxNoCipher(title.getTaxNo().getCipher());
            po.setTaxNoHash(title.getTaxNo().getHash());
            po.setTaxNoMask(title.getTaxNo().getMask());
        }
        po.setIsDefault(title.getIsDefault());
        po.setDeleted(title.getDeleted());
        return po;
    }

    private static EncryptedIdentifier toIdentifier(String cipher, String hash, String mask) {
        if (cipher == null && hash == null && mask == null) {
            return null;
        }
        return EncryptedIdentifier.fromStorage(cipher, hash, mask);
    }
}
