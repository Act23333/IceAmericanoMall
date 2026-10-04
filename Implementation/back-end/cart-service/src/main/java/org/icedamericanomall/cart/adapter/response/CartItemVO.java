package org.icedamericanomall.cart.adapter.response;

import lombok.Data;

/**
 * 购物车项视图对象（对外所需字段）。
 *
 * <p>仅暴露前端结算/展示必需字段；审技术字段（create_time/update_time）不外泄。
 */
@Data
public class CartItemVO {
    private Long skuId;
    private String productName;
    private String spec;
    private String image;
    private Integer price;
    private Integer quantity;
    private Boolean selected;
    private Integer subTotal;  // 价格*数量，单位分
}