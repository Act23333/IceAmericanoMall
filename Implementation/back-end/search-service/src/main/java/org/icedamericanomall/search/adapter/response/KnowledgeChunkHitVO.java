package org.icedamericanomall.search.adapter.response;

import lombok.Data;
import org.icedamericanomall.search.domain.model.search.KnowledgeChunkHit;

import java.io.Serial;
import java.io.Serializable;

/**
 * 知识 chunk 检索命中视图对象（内部端点 {@code POST /internal/search/vector} 的
 * {@code index=knowledge_vectors} 分支返回形状）。
 *
 * <p>与商品分支的 {@link SpuSearchVO} 互斥：同一端点按 {@code index} 返回不同形状，
 * 调用方（ai-service）按 index 装配。字段名即 JSON 契约，不暴露 {@code vector}。
 */
@Data
public class KnowledgeChunkHitVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long knowledgeId;
    private Integer chunkIndex;
    private Long sellerId;
    private String title;
    private String text;

    /** 领域命中 → 对外 VO（字段一一对应）。 */
    public static KnowledgeChunkHitVO from(KnowledgeChunkHit hit) {
        KnowledgeChunkHitVO vo = new KnowledgeChunkHitVO();
        vo.setKnowledgeId(hit.knowledgeId());
        vo.setChunkIndex(hit.chunkIndex());
        vo.setSellerId(hit.sellerId());
        vo.setTitle(hit.title());
        vo.setText(hit.text());
        return vo;
    }
}
