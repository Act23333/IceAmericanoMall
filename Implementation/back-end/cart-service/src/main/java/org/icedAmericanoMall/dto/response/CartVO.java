package org.icedAmericanoMall.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class CartVO {
    private List<CartItemResp> items;
    private Integer totalPrice;   // 总价（分）
    private Integer selectedPrice; // 选中商品总价（分）
    private Boolean allSelected;   // 是否全选
}