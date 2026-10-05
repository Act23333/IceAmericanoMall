package org.icedamericanomall.search.domain.model.search;

/**
 * 笔记向量 kNN 命中（领域读模型）—— 契约 I51 的领域形状（只含 noteId + score）。
 *
 * <p>发布态过滤与笔记卡组装归 item-service（MySQL 是展示真相源，ADR-046 同口径），
 * search-service 只负责相似度排序。
 *
 * @param noteId 命中笔记 id（排除自身由应用层负责）
 * @param score  相似度得分（ES kNN _score，越高越相似）
 */
public record NoteVectorHit(Long noteId, Double score) {
}
