package org.icedamericanomall.search.infrastructure.es;

/**
 * {@code knowledge_vectors} 索引 mapping 构造器（纯函数，便于单测）。
 *
 * <p>为什么用原生 JSON 而不是 Spring Data ES 注解：索引名与 dims 都是<b>运行时配置</b>，
 * 而 {@code @Field(dims=...)} 只接受编译期常量 —— 注解方案会把维度钉死在代码里，
 * 与「dims 全配置化」的要求冲突。故这里显式构造 mapping，交由低层客户端
 * {@code indices().create(...withJson(...))} 建索引。
 *
 * <p>mapping 语义（ES 7.17 dense_vector kNN）：
 * <ul>
 *   <li>{@code index.knn=true} —— 开启 kNN 检索（dense_vector index=true 的前置条件）；</li>
 *   <li>{@code vector.type=dense_vector, dims=N, index=true, similarity=cosine} —— 与
 *       {@code EmbeddingProperties.dims} 同源，维度不一致会导致 bulk 写入被 ES 拒绝（fail-loud）；</li>
 *   <li>{@code knowledgeId/sellerId/chunkIndex} 为数值精确匹配字段
 *       （删除按 knowledgeId term 查询、检索按 sellerId 过滤）。</li>
 * </ul>
 */
final class KnowledgeVectorMapping {

    /** 向量字段名（检索端 kNN 的 field，须与写入字段一致）。 */
    static final String VECTOR_FIELD = "vector";

    private KnowledgeVectorMapping() {
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
            throw new IllegalArgumentException("知识库向量维度必须为正数，实际: " + dims);
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
                      "knowledgeId": { "type": "long" },
                      "chunkIndex":  { "type": "integer" },
                      "sellerId":    { "type": "long" },
                      "title":       { "type": "text" },
                      "text":        { "type": "text" },
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
