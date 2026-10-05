package org.icedamericanomall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 图邻居查询结果（I58 返回，契约 §4.14.10b，V5.16）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KgNeighborCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 目标节点类型 */
    private String dstType;

    /** 目标节点 id */
    private Long dstId;

    /** 关系类型（到达 dst 所经的边） */
    private String relType;

    /** 排序权重（PAIR_WITH 生效） */
    private Integer weight;

    /** 检索评分（ES relevance，同分时按 weight/稳定序） */
    private Float score;
}
