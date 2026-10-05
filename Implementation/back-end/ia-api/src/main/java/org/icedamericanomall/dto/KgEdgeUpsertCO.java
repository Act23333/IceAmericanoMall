package org.icedamericanomall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 图边批量摄取入参（item-service → search-service，契约 §4.14.10b I57，V5.16）。
 *
 * <p>幂等：按 {@code srcType+srcId} 先删后写（对齐 I37/I47 模式，ADR-049 前先例）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KgEdgeUpsertCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 源节点类型：product | category */
    @NotBlank
    private String srcType;

    /** 源节点 id */
    @NotNull
    private Long srcId;

    /** 本次写入的边集合（先删该 src 全部旧边，再写本集合） */
    @NotNull
    private List<KgEdgeItemCO> edges;
}
