package org.icedamericanomall.domain.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class FlashBuyResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private Boolean success;
    private String orderNo;
    private Long flashId;
    private Integer flashPrice;
    private Long productId;
    private Long remainingStock;
}
