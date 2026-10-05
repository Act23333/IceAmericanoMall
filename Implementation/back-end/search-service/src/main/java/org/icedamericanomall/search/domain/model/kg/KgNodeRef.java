package org.icedamericanomall.search.domain.model.kg;

/**
 * 图节点引用（领域查询模型）—— 邻居查询的遍历起点（srcType + srcId 二元组）。
 *
 * <p>一跳查询以单个节点为起点；两跳查询以第一跳命中的全部目标节点为起点
 * （经 ID 引用遍历，不持有对象引用）。
 *
 * @param type 节点类型（product | category）
 * @param id   节点 id
 */
public record KgNodeRef(String type, Long id) {
}
