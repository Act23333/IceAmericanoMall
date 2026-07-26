package org.icedamericanomall.domain.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class FlashSaleOrderMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String orderNo;
    private Long flashId;
    private Long userId;
    private Long skuId;
    private Integer quantity;
}
