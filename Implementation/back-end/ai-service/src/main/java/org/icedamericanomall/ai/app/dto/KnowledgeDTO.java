package org.icedamericanomall.ai.app.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商家知识库条目（应用层传输对象）。
 *
 * <p>由 {@code MerchantKnowledgePO} 在应用层装配，适配层经 {@code KnowledgeConvertor} 转
 * {@code KnowledgeVO} 出参。刻意<b>不含</b> {@code embeddingId}：那是 RAG/向量库内部句柄
 * （当前从未写入），不属于对外字段。
 */
@Data
public class KnowledgeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long sellerId;

    private String title;

    private String content;

    private String category;

    /** 状态：2=生效，3=已删除（软删）。 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
