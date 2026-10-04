package org.icedamericanomall.trade.adapter.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 订单分页查询参数（GET 查询参数属性绑定，query 名保持 {@code status}/{@code page}/{@code size} 不变）。
 *
 * <p>同一形状服务于买卖家与管理员三处订单分页：
 * 买家 {@code GET /api/trade/order/page}、商家 {@code GET /api/trade/seller/order/page}、
 * 管理员 {@code GET /api/trade/admin/orders/page}；归属过滤（userId/sellerId）不在请求体中，
 * 由登录态与端点语义决定（禁止前端传归属 ID 越权查询）。
 *
 * <p>{@code page}/{@code size} 使用与 {@code @RequestParam(defaultValue=...)} 等价的字段初值，
 * 参数缺省时行为与迁移前完全一致。
 */
@Data
public class OrderPageReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单状态筛选，{@code null}=不限。 */
    private Integer status;

    /** 页码（从 1 开始）。 */
    private int page = 1;

    /** 每页条数。 */
    private int size = 20;
}
