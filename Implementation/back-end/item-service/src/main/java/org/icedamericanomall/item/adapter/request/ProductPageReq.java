package org.icedamericanomall.item.adapter.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class ProductPageReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long categoryId;

    /** 店铺过滤（S5：店铺页商品列表，sellerId 口径 = seller.user_id） */
    private Long sellerId;

    private String keyword;
    private String sort;      // "sales" or "price"
    private String order;     // "asc" or "desc"
    private Integer page = 1;
    private Integer size = 20;
}
