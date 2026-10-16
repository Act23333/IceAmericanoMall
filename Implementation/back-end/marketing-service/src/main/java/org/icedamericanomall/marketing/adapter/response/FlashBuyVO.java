package org.icedamericanomall.marketing.adapter.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 秒杀购买结果视图对象（适配层出参）—— {@code POST /api/flash/buy} 响应，
 * {@code orderNo} 为 trade-service 落库后的真实订单号。
 */
@Data
public class FlashBuyVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Boolean success;
    private String orderNo;
    private Long flashId;
    private Integer flashPrice;
    private Long spuId;
    private Long remainingStock;
}
