package org.icedamericanomall.marketing.app.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 秒杀购买结果（应用层传输对象）—— 秒杀域内部流转用。
 *
 * <p>{@code orderNo} 由 {@code MarketingManager} 在 trade-service 落单成功后回填；
 * 适配层经 {@code MarketingConverter} 转为 {@code FlashBuyVO} 出参，本类不外泄为响应体。
 */
@Data
public class FlashBuyResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private Boolean success;
    private String orderNo;
    private Long flashId;
    private Integer flashPrice;
    private Long productId;
    private Long skuId;
    private Long remainingStock;
}
