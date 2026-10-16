package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * SKU 跨服务契约对象（I13/I14/I15 —— 购物车回填、trade 下单取数、库存扣减）。
 *
 * <p><b>标识口径（ADR-058 §1 / ADR-061 §决策 2）</b>：线格式中出现的一切 SPU/SKU
 * <b>实体标识</b>一律为<b>业务键</b> 32-hex {@code String} ——
 * {@link #skuId}（{@code sku.sku_id}）、{@link #spuId}（{@code spu.spu_id}）。
 * 技术主键 {@link #id}（{@code sku.id}）与技术外键仅保留在 PO/领域层，<b>不得</b>用于
 * 跨服务/跨接口引用（分库分表后技术键不再全局唯一）。
 *
 * <p><b>名称字段（ADR-061 §决策 5）</b>：{@link #spuName} ← {@code spu.name}
 * （原 {@code productName}，词汇与 SPU 实体对齐）。
 */
@Data
public class SkuCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 技术主键（{@code sku.id}）—— 仅限同库内部解析用，不作为出线格式标识。 */
    private Long id;

    /** SKU 业务键（{@code sku.sku_id} 的 32-hex 形态）。 */
    private String skuId;

    /** SPU 业务键（{@code spu.spu_id} 的 32-hex 形态）。 */
    private String spuId;

    private Long sellerId;

    /** SPU 名（{@code spu.name}；原 {@code productName}）。 */
    private String spuName;

    private String spec;
    private Integer price;
    private Integer stock;
    private String image;
    private Integer status;

    // V4.0: 库存类型 + 热销标识 + 销售标签（京东/淘宝标准）
    private Integer stockType;   // 1=LIMITED, 2=UNLIMITED, 3=PRESALE
    private Boolean isHot;
    private String hotReason;
    private String salesTags;

    // V4.3: 品类ID（从商品表关联，用于优惠券品类范围校验）
    private Long categoryId;

    /**
     * V5.6（F2 运费模型）：商品绑定的运费模板 ID（从 spu 表关联，可为 null）。
     * <p>null = 未绑定 → 下单计价回落商家默认模板（最早创建的启用模板）；模板被删除时
     * 经 {@code ON DELETE SET NULL} 自动回落（见 04-Data-Model §十二 12.2）。
     */
    private Long freightTemplateId;
}
