package org.icedamericanomall.marketing.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 秒杀场次视图对象（适配层出参）—— 批量列表口径见 {@code GET /api/flash}（05-API-Specification 4.10.2）。
 *
 * <p><b>字段来源分两段</b>（契约 §4.10.2 / §8.9 {@code FlashSaleVO} 逐字对齐）：
 * <ol>
 *   <li>场次本体（{@code id} / {@code sku_id} / {@code flash_price} / {@code stock} /
 *       {@code sold_count} / 时间窗 / {@code status}）来自 {@link
 *       org.icedamericanomall.marketing.domain.model.flash.FlashSale} 聚合；</li>
 *   <li>商品展示字段（{@code product_name} / {@code product_image} / {@code original_price}）
 *       由应用层读模型按 SKU ID 批量回填（{@code FlashSaleViewManager}）——秒杀表只存
 *       商品/SKU ID，不存商品快照。</li>
 * </ol>
 *
 * <p><b>回填缺失（SKU 已删除 / 数据源降级）时三个商品字段保持 {@code null}</b>：
 * 秒杀列表照常返回，不因补展示字段而变脆（fail-open）。
 *
 * <p><b>契约漂移记录</b>：{@code spu_id} 为实现已投影、契约样例暂未收录的字段
 * （前端订单跳转依赖它），已在交付报告中作为「契约待补字段」登记。
 */
@Data
public class FlashSaleVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 商品 ID（契约样例暂缺，实现投影；前端详情跳转依赖） */
    private Long spuId;

    private Long skuId;

    /** 商品名称（读时回填；缺失为 null） */
    private String productName;

    /** 商品主图（读时回填；缺失为 null） */
    private String productImage;

    /** 秒杀价（分） */
    private Integer flashPrice;

    /** 划线原价（分；取 SKU {@code original_price}，缺失为 null） */
    private Integer originalPrice;

    /** 秒杀总库存 */
    private Integer stock;

    private Integer soldCount;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer status;

    /** 已售进度百分比（整数 0–100；= sold_count / stock，见 {@code FlashSale#progressPercent}） */
    private Integer progressPercent;
}
