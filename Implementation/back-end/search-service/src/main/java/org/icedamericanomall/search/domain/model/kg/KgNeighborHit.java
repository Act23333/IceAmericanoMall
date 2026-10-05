package org.icedamericanomall.search.domain.model.kg;

/**
 * 图邻居命中（领域读模型）—— 一跳 terms 检索返回的一条邻居边。
 *
 * @param dstType 目标节点类型
 * @param dstId   目标节点 id
 * @param relType 关系类型（到达 dst 所经的边）
 * @param weight  排序权重（PAIR_WITH 生效）
 * @param score   检索评分（ES relevance，同分按 weight/稳定序）
 */
public record KgNeighborHit(String dstType, Long dstId, String relType, Integer weight, Float score) {
}
