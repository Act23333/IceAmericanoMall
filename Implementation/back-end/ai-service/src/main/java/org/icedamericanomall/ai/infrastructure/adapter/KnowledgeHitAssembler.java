package org.icedamericanomall.ai.infrastructure.adapter;

import org.icedamericanomall.ai.domain.model.knowledge.KnowledgeHit;

import java.util.Map;

/**
 * 知识库 chunk 命中 Map → {@link KnowledgeHit} 装配器（基础设施层内部工具）。
 *
 * <p>search-service 的向量端点按 {@code index} 返回双形状报文；知识分支字段固定为
 * {@code knowledgeId/chunkIndex/title/text}，集中在此做类型归一（数值可能为 Integer/Long/String），
 * 避免各适配器重复解析。
 */
final class KnowledgeHitAssembler {

    private KnowledgeHitAssembler() {
    }

    /** 从检索结果 Map 装配知识命中项。 */
    static KnowledgeHit fromMap(Map<?, ?> raw) {
        return new KnowledgeHit(
                asLong(raw.get("knowledgeId")),
                asInt(raw.get("chunkIndex")),
                asString(raw.get("title")),
                asString(raw.get("text")));
    }

    private static Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Integer asInt(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
