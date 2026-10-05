package org.icedamericanomall.search.infrastructure.es;

/**
 * {@code products} 索引向量 mapping 构造器（纯函数，便于单测）—— 商品向量推荐 I47/I48 的
 * 建索引前置（ADR-046：I47 摄取为日常增量主路径，reindex 为全量兜底）。
 *
 * <p>与 {@link KnowledgeVectorMapping} 同理：索引设置与 dims 都是运行时配置，
 * 注解方案会把维度钉死为编译期常量，故显式构造 mapping 交由低层客户端建索引。
 *
 * <p>mapping 语义（ES 7.17 dense_vector kNN）：
 * <ul>
 *   <li>{@code index.knn=true} —— 开启 kNN 检索（dense_vector index=true 的前置条件）；</li>
 *   <li>{@code embedding.type=dense_vector, dims=N, index=true, similarity=cosine} ——
 *       与 {@code EmbeddingProperties.dims} 同源（维度单一事实源）；</li>
     *   <li>{@code name/description} 为 text（I28 关键词 match 检索字段），其余展示字段
     *       显式定类型，避免动态映射漂移；</li>
     *   <li>{@code publishTime} 为 date（SE1 newest 排序，写入侧用 epoch millis 数值，
     *       ES date 原生支持且数值可比）。</li>
 * </ul>
 */
final class ProductVectorMapping {

    /** 向量字段名（须与 {@link ProductDocument#getEmbedding()} 字段名一致）。 */
    static final String VECTOR_FIELD = "embedding";

    private ProductVectorMapping() {
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
            throw new IllegalArgumentException("商品向量维度必须为正数，实际: " + dims);
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
                      "id":          { "type": "long" },
                      "productId":   { "type": "keyword" },
                      "categoryId":  { "type": "long" },
                      "name":        { "type": "text" },
                      "description": { "type": "text" },
                      "brand":       { "type": "keyword" },
                      "mainImage":   { "type": "keyword" },
                      "price":       { "type": "integer" },
                      "soldCount":   { "type": "integer" },
                      "publishTime": { "type": "date" },
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
