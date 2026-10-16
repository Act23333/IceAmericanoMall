package org.icedamericanomall.marketing.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 秒杀场次视图对象（适配层出参）—— 批量列表口径见 {@code GET /api/flash}（05-API-Specification 4.10.2）。
 *
 * <p><b>字段来源分两段</b>（契约 §4.10.2 / §8.9 {@code FlashSaleVO} 逐字对齐）：
 * <ol>
 *   <li>场次本体（{@code id} / {@code flash_price} / {@code stock} /
 *       {@code sold_count} / 时间窗 / {@code status}）来自
 *       {@link org.icedamericanomall.marketing.domain.model.flash.FlashSale} 聚合；</li>
 *   <li>商品标识与展示字段（{@code spu_id} / {@code sku_id} / {@code spu_name} /
 *       {@code main_image} / {@code original_price}）由应用层读模型按 SKU 技术键批量回填
 *       （{@code FlashSaleViewManager}）—— 秒杀表只存技术键，不存商品快照。</li>
 * </ol>
 *
 * <p><b>标识与命名（ADR-061）</b>：{@code spu_id}/{@code sku_id} 为 <b>业务键</b> 32-hex
 * {@code String}（原 {@code Long} 技术键）；SPU 名称字段收口为 {@code spu_name}
 * （原 {@code product_name}），主图为 <b>{@code main_image}</b>
 * （与 DB 列 {@code spu.main_image} 及各 VO 同名 —— 消除「同一概念两种线上名」方言）。
 *
 * <p><b>回填缺失（SKU 已删除 / 数据源降级）时商品字段保持 {@code null}</b>：
 * 秒杀列表照常返回，不因补展示字段而变脆（fail-open）。
 */
@Data
public class FlashSaleVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 场次 ID（技术主键，渲染/定位用）。 */
    private Long id;

    /** SPU 业务键（32-hex；读时回填，缺失为 null）。 */
    private String spuId;

    /** SKU 业务键（32-hex；读时回填，缺失为 null）。 */
    private String skuId;

    /** SPU 名（读时回填；缺失为 null）。 */
    private String spuName;

    /** SPU 主图（读时回填；缺失为 null）—— 线上名统一 {@code main_image}。 */
    private String mainImage;

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
