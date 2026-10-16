package org.icedamericanomall.search.adapter.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.List;

/**
 * 图边批量摄取入参（内部端点 {@code POST /internal/search/kg/edge/upsert}，契约 I57）。
 *
 * <p>字段与 ia-api {@code KgEdgeUpsertCO} 一一对应（Controller 负责 Req→CO 搬运）；
 * 幂等口径：按 {@code srcType+srcId} 先删后写。
 *
 * @param srcType 源节点类型（spu | category）
 * @param srcId   源节点 id
 * @param edges   本次写入的边集合（空集合 = 清空该 src 全部旧边）
 */
public record KgEdgeUpsertReq(
        @NotBlank(message = "srcType 不能为空") String srcType,
        @NotNull(message = "srcId 不能为空") Long srcId,
        @NotNull(message = "edges 不能为空") @Valid List<KgEdgeItemReq> edges
) implements Serializable {

    /**
     * 单条边（字段与 ia-api {@code KgEdgeItemCO} 一一对应）。
     *
     * @param relType 关系类型（BELONGS_TO / PARENT_OF / BRAND_OF / SOLD_BY / PAIR_WITH）
     * @param dstType 目标节点类型
     * @param dstId   目标节点 id
     * @param weight  排序权重（PAIR_WITH 生效；直建边缺省 0）
     * @param source  边来源（DIRECT / RULE）
     * @param status  1=启用 0=停用（缺省 1）
     */
    public record KgEdgeItemReq(
            @NotBlank(message = "relType 不能为空") String relType,
            @NotBlank(message = "dstType 不能为空") String dstType,
            @NotNull(message = "dstId 不能为空") Long dstId,
            Integer weight,
            @NotBlank(message = "source 不能为空") String source,
            Integer status
    ) implements Serializable {
    }
}
