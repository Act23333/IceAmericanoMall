package org.icedamericanomall.marketing.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 秒杀场次视图对象（适配层出参）—— 批量列表口径见 {@code GET /api/flash}（05-API-Specification 4.10.2）。
 */
@Data
public class FlashSaleVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long productId;
    private Long skuId;
    private Integer flashPrice;
    private Integer stock;
    private Integer soldCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
}
