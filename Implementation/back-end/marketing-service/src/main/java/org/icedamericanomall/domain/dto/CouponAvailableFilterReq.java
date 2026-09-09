package org.icedamericanomall.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * V4.3: 优惠券可用性过滤请求（京东标准：结算页只返回可用的券）。
 * 前端结算时传入订单上下文，后端返回适用+不适用的券列表（含不可用原因）。
 */
@Data
public class CouponAvailableFilterReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 订单类型: 1=NORMAL, 2=DIRECT, 3=FLASH_SALE */
    private Integer orderType;
    /** 订单卖家ID（购物车下单时来自购物车商品，立即购买时来自SKU） */
    private Long sellerId;
    /** 订单中的SKU ID列表 */
    private List<Long> skuIds;
    /** SKU对应的品类ID列表（前端从商品详情获取） */
    private List<Long> categoryIds;
    /** 订单预估总金额(分) */
    private Integer totalAmount;
}
