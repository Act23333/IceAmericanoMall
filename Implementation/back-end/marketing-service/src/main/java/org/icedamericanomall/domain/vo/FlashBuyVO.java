package org.icedamericanomall.domain.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class FlashBuyVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Boolean success;
    private String orderNo;
    private Long flashId;
    private Integer flashPrice;
    private Long productId;
    private Long remainingStock;
}
