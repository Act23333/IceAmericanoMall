package org.icedamericanomall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 相似商品命中（search-service → item-service，契约 §4.14.10 I48，V5.9）。
 *
 * <p>只出 {@code spuId + score}——商品卡的名称/价格/图等展示字段由 item 侧
 * 查本地库组装（MySQL 是展示真相源，ES 文档可能滞后，ADR-046）。
 *
 * <p><b>标识口径（ADR-061 §决策 2）</b>：{@code spuId} 是 SPU <b>业务键</b>
 * （{@code spu.spu_id} 的 32-hex 形态），不是技术主键 —— 与 {@code SkuCO.spuId} /
 * {@code SkuVO.spuId} 同源；item 侧按下游聚合的业务键逐位比对/join，
 * 技术键解析留在同库读模型内（A 案：表结构不动）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpuVectorHitCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 命中商品的 <b>SPU 业务键</b>（32-hex，ADR-061；已排除自身） */
    private String spuId;

    /** 向量相似度得分（越高越相似，口径由 ES kNN 决定） */
    private double score;
}
