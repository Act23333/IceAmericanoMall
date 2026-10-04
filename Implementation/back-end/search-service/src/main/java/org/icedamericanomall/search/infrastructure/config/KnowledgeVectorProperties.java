package org.icedamericanomall.search.infrastructure.config;

import lombok.Data;
import org.icedamericanomall.client.KnowledgeVectorClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 知识库向量索引配置 —— {@code knowledge_vectors} 索引名与检索 Top-K 缺省值。
 *
 * <p>索引名配置化（不硬编码）便于多环境隔离（如 dev 用 {@code knowledge_vectors_dev}）
 * 与灰度重建；dims 不在此处，唯一来源是 {@link EmbeddingProperties#getDims()}。
 *
 * <p>注意区分两个概念：<b>契约索引名</b>（{@link KnowledgeVectorClient#INDEX_KNOWLEDGE_VECTORS}，
 * 调用方在请求里传的取值，决定走哪条检索分支）与<b>物理索引名</b>（本配置，
 * 决定 ES 里实际读写的 index）。二者默认一致，改物理名不影响调用方契约。
 */
@Data
@Component
@ConfigurationProperties(prefix = "search.knowledge-vector")
public class KnowledgeVectorProperties {

    /** 物理索引名（默认与契约索引名一致）。 */
    private String indexName = KnowledgeVectorClient.INDEX_KNOWLEDGE_VECTORS;

    /** kNN 检索缺省 Top-K（调用方未指定或非法时使用）。 */
    private int defaultTopK = 5;

    /** 单条目最大 chunk 数（防止超长文档写入过多 doc）。 */
    private int maxChunks = 50;
}
