package org.icedamericanomall.search.infrastructure.es;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.io.Serializable;

/**
 * 笔记 ES 索引文档（基础设施层）——映射 {@code notes} 索引（契约 I50/I51，V5.13）。
 *
 * <p>与商品 {@link ProductDocument} 同构：原文（title/content）+ 稠密向量，
 * _id = noteId（幂等覆盖键）。索引名常量供网关建索引与 mapping 共用（单一事实源）。
 */
@Data
@Document(indexName = NoteVectorDocument.INDEX_NAME)
public class NoteVectorDocument implements Serializable {

    /** 笔记向量索引名（契约 I50/I51；独立小索引，单向量不分块，ADR-046）。 */
    public static final String INDEX_NAME = "notes";

    private static final long serialVersionUID = 1L;

    /** 笔记 id（幂等键，ES 文档 _id 同源）。 */
    @Id
    private Long noteId;

    /** 笔记标题（向量化文本主来源，I28 式关键词字段保留以便后续扩展）。 */
    private String title;

    /** 笔记正文（向量化文本）。 */
    private String content;

    /** 笔记向量（dense_vector，dims 同源 Embedding 配置），仅索引内部使用。 */
    private double[] embedding;
}
