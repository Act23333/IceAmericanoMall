package org.icedamericanomall.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderNo;
    private Long userId;
    private Long sellerId;
    private Integer totalAmount;
    private Integer payAmount;
    private Integer discountAmount;
    private Integer status;
    private Integer orderType;   // V4.1: 1=NORMAL, 2=DIRECT, 3=FLASH_SALE, 4=PRESALE
    private Integer paymentType;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime consignTime;
    private LocalDateTime endTime;
    private List<OrderItemVO> items;
}
