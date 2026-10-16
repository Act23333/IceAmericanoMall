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
    /** SPU 业务键（32-hex）—— 由 {@code MarketingManager} 经读模型解析后回填（ADR-061 §决策 2）。 */
    private String spuBusinessId;
    /** SKU 业务键（32-hex）—— 同上；用于 I14 下单入参与线格式出参。 */
    private String skuBusinessId;
    /** SKU 技术键（{@code sku.id}）—— 仅限本域内部（读模型解析入参），不出线格式。 */
    private Long skuId;
    private Long remainingStock;
}
