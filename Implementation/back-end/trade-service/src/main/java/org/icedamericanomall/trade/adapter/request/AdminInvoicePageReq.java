package org.icedamericanomall.trade.adapter.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理端发票分页入参（AD57 {@code GET /api/admin/invoice/page}，契约 §4.13.8e）。
 *
 * <p>筛选：状态 / 商家 / 部分退款标记（"待人工处理"默认筛选视图，D4/O6）；
 * {@code urgeOnly=true} 时仅返回有催开记录的发票并按 urge_time 倒序（O10 催开排序视图）。
 */
@Data
public class AdminInvoicePageReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 发票状态筛选（1 待开具 2 已开具 3 已冲红 4 已作废），{@code null}=不限。 */
    private Integer status;

    /** 开票主体商家 user_id 筛选，{@code null}=不限。 */
    private Long sellerId;

    /** 部分退款待人工处理标记筛选（true=仅待人工处理），{@code null}=不限。 */
    private Boolean partialRefund;

    /** 是否催开排序视图（true=仅看有催开记录并按 urge_time 倒序，O10）。 */
    private Boolean urgeOnly;

    /** 页码（从 1 开始）。 */
    private int page = 1;

    /** 每页条数。 */
    private int size = 20;
}
