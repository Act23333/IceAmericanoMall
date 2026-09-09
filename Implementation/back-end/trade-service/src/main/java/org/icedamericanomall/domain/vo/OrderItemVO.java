package org.icedamericanomall.domain.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class OrderItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long skuId;
    private String productName;
    private String skuSpec;
    private Integer price;
    private Integer quantity;
    private Integer subTotal;
    private String image;
}
