package org.icedamericanomall.user.adapter.response;

import lombok.Data;
import org.icedamericanomall.user.infrastructure.persistence.knowledge.MerchantKnowledgeDO;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商家知识库条目视图对象 —— 替代原直接返回 DO 的报文（字段与重构前完全一致）。
 */
@Data
public class KnowledgeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long sellerId;

    private String title;

    private String content;

    private String category;

    private String embeddingId;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 持久化对象 → 视图对象。 */
    public static KnowledgeVO from(MerchantKnowledgeDO dataObject) {
        KnowledgeVO vo = new KnowledgeVO();
        vo.setId(dataObject.getId());
        vo.setSellerId(dataObject.getSellerId());
        vo.setTitle(dataObject.getTitle());
        vo.setContent(dataObject.getContent());
        vo.setCategory(dataObject.getCategory());
        vo.setEmbeddingId(dataObject.getEmbeddingId());
        vo.setStatus(dataObject.getStatus());
        vo.setCreateTime(dataObject.getCreateTime());
        vo.setUpdateTime(dataObject.getUpdateTime());
        return vo;
    }
}
