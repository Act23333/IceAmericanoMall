package org.icedamericanomall.trade.adapter.assembler;

import org.icedamericanomall.trade.adapter.response.InvoiceTitleVO;
import org.icedamericanomall.trade.adapter.response.InvoiceVO;
import org.icedamericanomall.trade.domain.model.invoice.Invoice;
import org.icedamericanomall.trade.domain.model.invoice.InvoiceTitle;
import org.springframework.stereotype.Component;

/**
 * 发票聚合 → VO 装配器（Adapter 层 Assembler）。
 *
 * <p>手写而非 MapStruct：PII 掩码取自 {@code EncryptedIdentifier.mask}、样票下载地址
 * 由开具端口预签名解析，均为条件装配逻辑（对齐 {@code TradeConverter.toSellerVO} 的
 * 手写 default 方法口径）。VO 只出掩码，禁止 cipher/hash 外泄。
 */
@Component
public class InvoiceAssembler {

    /** 非默认标记输出值（is_default NULL 归一为 0，前端免判空）。 */
    private static final int NOT_DEFAULT_FLAG = 0;

    /**
     * 发票聚合 → VO（税号/邮箱取掩码；样票地址由调用方按状态解析后传入）。
     *
     * @param invoice        发票聚合
     * @param pdfDownloadUrl 样票预签名下载地址（仅已开具传入；其余状态传 null）
     * @return 脱敏后的 VO
     */
    public InvoiceVO toVO(Invoice invoice, String pdfDownloadUrl) {
        InvoiceVO vo = new InvoiceVO();
        vo.setInvoiceNo(invoice.getInvoiceNo());
        vo.setOrderNo(invoice.getOrderNo());
        vo.setUserId(invoice.getUserId());
        vo.setSellerId(invoice.getSellerId());
        vo.setType(invoice.getType());
        vo.setTitle(invoice.getTitle());
        vo.setTaxNoMask(invoice.getTaxNo() == null ? null : invoice.getTaxNo().getMask());
        vo.setEmailMask(invoice.getEmail() == null ? null : invoice.getEmail().getMask());
        vo.setAmount(invoice.getAmount());
        vo.setStatus(invoice.getStatus());
        vo.setIssueTime(invoice.getIssueTime());
        vo.setPartialRefundFlag(invoice.getPartialRefundFlag());
        vo.setPdfKey(pdfDownloadUrl);
        vo.setCreateTime(invoice.getCreateTime());
        return vo;
    }

    /**
     * 常用抬头聚合 → VO（税号取掩码；默认标记 NULL 归一为 0）。
     *
     * @param title 抬头聚合
     * @return 脱敏后的 VO
     */
    public InvoiceTitleVO toTitleVO(InvoiceTitle title) {
        InvoiceTitleVO vo = new InvoiceTitleVO();
        vo.setId(title.getId());
        vo.setType(title.getType());
        vo.setTitle(title.getTitle());
        vo.setTaxNoMask(title.getTaxNo() == null ? null : title.getTaxNo().getMask());
        vo.setIsDefault(title.isDefaultTitle() ? InvoiceTitle.DEFAULT_FLAG : NOT_DEFAULT_FLAG);
        vo.setCreateTime(title.getCreateTime());
        return vo;
    }
}
