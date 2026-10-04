package org.icedamericanomall.ai.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商家知识库条目视图对象（适配层出参）—— 报文形状除 {@code embeddingId} 外与迁移前一致。
 *
 * <p><b>VO 修剪</b>：移除 {@code embeddingId} —— 向量库/ES 内部句柄（当前从未写入），属技术字段，
 * 不外泄给调用方；其余业务字段全部保留。装配由 {@code adapter/assembler/KnowledgeConvertor}
 * 从应用层 {@code KnowledgeDTO} 完成。
 */
@Data
public class KnowledgeVO implements Serializable {

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
