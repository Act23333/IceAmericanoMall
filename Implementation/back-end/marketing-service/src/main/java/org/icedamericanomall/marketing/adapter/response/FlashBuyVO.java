package org.icedamericanomall.marketing.adapter.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 秒杀购买结果视图对象（适配层出参）—— {@code POST /api/flash/buy} 响应，
 * {@code orderNo} 为 trade-service 落库后的真实订单号。
 *
 * <p><b>标识口径（ADR-061 §决策 2）</b>：{@code spuId} 为 <b>SPU 业务键</b> 32-hex
 * {@code String}（原 {@code Long} 技术键）；与 {@code FlashSaleVO.spu_id} 同型同义。
 */
@Data
public class FlashBuyVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Boolean success;
    private String orderNo;
    private Long flashId;
    private Integer flashPrice;
    /** SPU 业务键（32-hex）。 */
    private String spuId;
    private Long remainingStock;
}
