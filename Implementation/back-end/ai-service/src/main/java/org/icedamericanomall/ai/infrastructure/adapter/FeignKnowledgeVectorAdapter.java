package org.icedamericanomall.ai.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.ai.domain.model.knowledge.KnowledgeVectorDoc;
import org.icedamericanomall.ai.domain.port.KnowledgeVectorPort;
import org.icedamericanomall.client.KnowledgeVectorClient;
import org.icedamericanomall.dto.KnowledgeVectorCO;
import org.springframework.stereotype.Component;

/**
 * {@link KnowledgeVectorPort} 的 Feign 实现（防腐层）——把本上下文的知识条目
 * 转换为 search-service 的摄取报文，并保证<b>永不抛异常</b>（fail-open）。
 *
 * <p>降级语义与端口 Javadoc 一致：失败返回 {@link KnowledgeVectorPort#UPSERT_FAILED} / {@code false}，
 * 由调用方决定是否更新 embeddingId。这里吞掉异常并记 WARN，是因为知识库保存/删除的
 * 主流程不应被向量索引故障打断（索引可后续补偿重建）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeignKnowledgeVectorAdapter implements KnowledgeVectorPort {

    private final KnowledgeVectorClient knowledgeVectorClient;

    @Override
    public int upsertKnowledgeVector(KnowledgeVectorDoc doc) {
        if (doc == null || doc.knowledgeId() == null) {
            return UPSERT_FAILED;
        }
        KnowledgeVectorCO co = new KnowledgeVectorCO();
        co.setKnowledgeId(doc.knowledgeId());
        co.setSellerId(doc.sellerId());
        co.setTitle(doc.title());
        co.setContent(doc.content());
        try {
            Integer chunks = knowledgeVectorClient.upsertKnowledgeVectors(co);
            if (chunks == null || chunks < 0) {
                log.warn("知识库向量摄取未生效: knowledgeId={}, chunks={}", doc.knowledgeId(), chunks);
                return UPSERT_FAILED;
            }
            return chunks;
        } catch (Exception e) {
            log.warn("知识库向量摄取失败（降级，不影响知识库主流程）: knowledgeId={}, cause={}",
                    doc.knowledgeId(), e.getMessage());
            return UPSERT_FAILED;
        }
    }

    @Override
    public boolean deleteKnowledgeVector(Long knowledgeId) {
        if (knowledgeId == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(knowledgeVectorClient.deleteKnowledgeVectors(knowledgeId));
        } catch (Exception e) {
            log.warn("知识库向量删除失败（降级，不影响知识库主流程）: knowledgeId={}, cause={}",
                    knowledgeId, e.getMessage());
            return false;
        }
    }
}
