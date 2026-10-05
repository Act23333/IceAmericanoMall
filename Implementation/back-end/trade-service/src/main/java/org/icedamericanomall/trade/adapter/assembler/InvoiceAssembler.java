package org.icedamericanomall.trade.adapter.assembler;

import org.icedamericanomall.trade.adapter.response.InvoiceVO;
import org.icedamericanomall.trade.domain.model.invoice.Invoice;
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
}
