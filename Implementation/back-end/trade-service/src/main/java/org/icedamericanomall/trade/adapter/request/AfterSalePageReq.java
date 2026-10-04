package org.icedamericanomall.trade.adapter.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 售后分页查询参数（管理员 {@code GET /api/admin/after-sale}），
 * query 名保持 {@code status}/{@code page}/{@code size} 不变。
 */
@Data
public class AfterSalePageReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 售后状态筛选，{@code null}=不限。 */
    private Integer status;

    /** 页码（从 1 开始）。 */
    private int page = 1;

    /** 每页条数。 */
    private int size = 20;
}
