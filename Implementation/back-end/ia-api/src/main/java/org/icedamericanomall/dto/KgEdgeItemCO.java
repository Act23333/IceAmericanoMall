package org.icedamericanomall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 图边条目（I57 摄取载荷；Phase 1 边类型 BELONGS_TO / PARENT_OF / BRAND_OF / SOLD_BY / PAIR_WITH）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KgEdgeItemCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关系类型（BELONGS_TO / PARENT_OF / BRAND_OF / SOLD_BY / PAIR_WITH） */
    @NotBlank
    private String relType;

    /** 目标节点类型：spu | category | brand | seller */
    @NotBlank
    private String dstType;

    /** 目标节点 id（brand 为规范化 brand_key 字符串的 hash 占位——Phase 1 值节点，O4） */
    @NotNull
    private Long dstId;

    /** 排序权重（PAIR_WITH 生效；直建边 0） */
    private Integer weight;

    /** 边来源：DIRECT（直建）/ RULE（规则配置）；Phase 2 增 EXTRACTED */
    @NotBlank
    private String source;

    /** 1=启用 0=停用 */
    private Integer status;
}
