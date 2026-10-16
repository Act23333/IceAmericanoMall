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
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpuVectorHitCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 命中商品 id（已排除自身） */
    private Long spuId;

    /** 向量相似度得分（越高越相似，口径由 ES kNN 决定） */
    private double score;
}
