package org.icedamericanomall.trade.adapter.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 我的发票分页入参（INV2 {@code GET /api/invoice/history}，契约 §4.15），
 * query 名保持 {@code status}/{@code page}/{@code size}（对齐 {@code AfterSalePageReq} 先例）。
 */
@Data
public class InvoiceHistoryReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 发票状态筛选（1 待开具 2 已开具 3 已冲红 4 已作废），{@code null}=不限。 */
    private Integer status;

    /** 页码（从 1 开始）。 */
    private int page = 1;

    /** 每页条数。 */
    private int size = 20;
}
