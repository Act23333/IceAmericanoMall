package org.icedAmericanoMall.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class FlashOrderVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String orderNo;
    private Long flashId;
    private Long skuId;
    private Integer quantity;
    private Integer flashPrice;
    private Integer status;
    private String failReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
