package org.icedamericanomall.search.infrastructure.es;

/**
 * {@code notes} 索引向量 mapping 构造器（纯函数，便于单测）—— 笔记向量 I50/I51 的建索引前置
 * （契约 V5.13，ADR-046 管道第二次复用，与 {@link ProductVectorMapping} 同构）。
 *
 * <p>与商品/知识库 mapping 同理：索引设置与 dims 都是运行时配置，
 * 注解方案会把维度钉死为编译期常量，故显式构造 mapping 交由低层客户端建索引。
 *
 * <p>mapping 语义（ES dense_vector kNN）：
 * <ul>
 *   <li>{@code index.knn=true} —— 开启 kNN 检索（dense_vector index=true 的前置条件）；</li>
 *   <li>{@code embedding.type=dense_vector, dims=N, index=true, similarity=cosine} ——
 *       与 {@code EmbeddingProperties.dims} 同源（维度单一事实源）；</li>
 *   <li>{@code title/content} 为 text（向量化原文留档，独立小索引单向量不分块）。</li>
 * </ul>
 */
final class NoteVectorMapping {

    /** 向量字段名（须与 {@link NoteVectorDocument#getEmbedding()} 字段名一致）。 */
    static final String VECTOR_FIELD = "embedding";

    private NoteVectorMapping() {
    }

    /**
     * 构造建索引请求体（settings + mappings）。
     *
     * @param dims 向量维度（必须与 embedding 模型输出一致）
     * @return mapping JSON
     * @throws IllegalArgumentException dims 非法（≤0）—— 配置错误必须在建索引前暴露
     */
    static String buildJson(int dims) {
        if (dims <= 0) {
            throw new IllegalArgumentException("笔记向量维度必须为正数，实际: " + dims);
        }
        return """
                {
                  "settings": {
                    "index": {
                      "number_of_shards": 1,
                      "number_of_replicas": 0,
                      "knn": true
                    }
                  },
                  "mappings": {
                    "properties": {
                      "noteId":    { "type": "long" },
                      "title":     { "type": "text" },
                      "content":   { "type": "text" },
                      "%s": {
                        "type": "dense_vector",
                        "dims": %d,
                        "index": true,
                        "similarity": "cosine"
                      }
                    }
                  }
                }
                """.formatted(VECTOR_FIELD, dims);
    }
}
