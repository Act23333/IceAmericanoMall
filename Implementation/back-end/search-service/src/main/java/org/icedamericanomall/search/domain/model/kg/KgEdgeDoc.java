package org.icedamericanomall.search.domain.model.kg;

/**
 * 图边文档（领域写模型）—— {@code kg_edges} 边索引的一条边（PRD §16.4 边文档结构的写入侧投影）。
 *
 * <p>不可变值对象：权重/状态在摄取时定死；{@code updateTime} 与 {@code edgeId}
 * 是持久化细节（写时间戳 / 幂等 _id），由基础设施层在落 ES 时派生，不进领域模型。
 *
 * @param srcType 源节点类型（product | category）
 * @param srcId   源节点 id
 * @param relType 关系类型（BELONGS_TO / PARENT_OF / BRAND_OF / SOLD_BY / PAIR_WITH）
 * @param dstType 目标节点类型（product | category | brand | seller）
 * @param dstId   目标节点 id
 * @param weight  排序权重（PAIR_WITH 生效；直建边 0）
 * @param source  边来源（DIRECT 直建 / RULE 规则配置）
 * @param status  1=启用 0=停用
 */
public record KgEdgeDoc(String srcType, Long srcId, String relType,
                        String dstType, Long dstId,
                        Integer weight, String source, Integer status) {
}
