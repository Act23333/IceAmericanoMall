package org.icedamericanomall.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品向量摄取入参（item-service → search-service，契约 §4.14.10 I47，V5.9）。
 *
 * <p>幂等覆盖写：同 {@code spuId} 重写整份商品向量文档（ADR-046 单向量不分块）。
 *
 * <p><b>标识口径（ADR-061 §决策 2）</b>：{@code spuId} 是 SPU <b>业务键</b>
 * （{@code spu.spu_id} 的 32-hex 形态，由 {@code Spu.spuId} 直接带出），
 * 不是技术主键 —— 幂等键语义不变（一 SPU 一键），仅线格式类型收口。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpuVectorUpsertCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SPU 业务键（32-hex，幂等键，ADR-061 §决策 2） */
    @NotBlank
    private String spuId;

    /** SPU 名（向量化文本主来源；线上键 {@code spu_name}，契约 §1.8④ / I47 入参，原 {@code name}） */
    @NotBlank
    private String spuName;

    /** 商品描述（向量化文本） */
    private String description;

    /** 品牌 */
    private String brand;

    /** 类目 id */
    private Long categoryId;

    /** 主图 */
    private String mainImage;

    /** 展示价（分，取最小 SKU 价等由调用方决定） */
    private Integer price;

    /** 销量 */
    private Integer soldCount;
}
