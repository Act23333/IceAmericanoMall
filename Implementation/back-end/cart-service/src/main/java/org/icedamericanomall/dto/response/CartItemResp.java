package org.icedamericanomall.dto.response;

import lombok.Data;

@Data
public class CartItemResp {
    private Long skuId;
    private String productName;
    private String spec;
    private String image;
    private Integer price;
    private Integer quantity;
    private Boolean selected;
    private Integer subTotal;  // 价格*数量，单位分
}