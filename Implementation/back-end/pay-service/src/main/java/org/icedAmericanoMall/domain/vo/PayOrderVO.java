package org.icedAmericanoMall.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 支付单视图对象 —— 对外返回，屏蔽内部字段（expandJson/resultMsg/时间戳等）。
 */
@Data
public class PayOrderVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String payOrderNo;
    private String bizOrderNo;
    private Long bizUserId;
    private String payChannelCode;
    private Integer amount;
    private Integer payType;
    private Integer status;
    private String qrCodeUrl;
    private LocalDateTime payOverTime;
    private LocalDateTime paySuccessTime;
}
