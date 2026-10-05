package org.icedamericanomall.search.infrastructure.es;

import org.icedamericanomall.search.domain.model.kg.KgEdgeDoc;

/**
 * {@code kg_edges} 边索引 mapping 构造器（纯函数，便于单测）。
 *
 * <p>与 {@link KnowledgeVectorMapping} 同款「原生 JSON + 低层客户端建索引」方案：
 * 结构随 PRD §16.4 定稿即冻结，无运行时可变参数（非向量索引，无需 dims 注入）。
 *
 * <p>mapping 语义（ES 7.17，非向量）：
 * <ul>
 *   <li>{@code edgeId/srcType/relType/dstType/source} 为 keyword 精确字段
 *       （terms 查询与 _id 派生的唯一口径）；</li>
 *   <li>{@code srcId/dstId} 为 long、{@code weight/status} 为 integer、
 *       {@code updateTime} 为 long（epoch 毫秒）——数值精确匹配，不做分词；</li>
 *   <li>刻意<b>无</b> dense_vector 字段：边检索是 terms 组合（≤2 跳），不走 kNN。</li>
 * </ul>
 */
final class KgEdgeMapping {

    /** 物理索引名（契约 §4.14.10b 定名 kg_edges）。 */
    static final String INDEX_NAME = "kg_edges";

    static final String FIELD_EDGE_ID = "edgeId";
    static final String FIELD_SRC_TYPE = "srcType";
    static final String FIELD_SRC_ID = "srcId";
    static final String FIELD_REL_TYPE = "relType";
    static final String FIELD_DST_TYPE = "dstType";
    static final String FIELD_DST_ID = "dstId";
    static final String FIELD_WEIGHT = "weight";
    static final String FIELD_SOURCE = "source";
    static final String FIELD_STATUS = "status";
    static final String FIELD_UPDATE_TIME = "updateTime";

    private KgEdgeMapping() {
    }

    /**
     * 构造建索引请求体（settings + mappings）。
     *
     * @return mapping JSON
     */
    static String buildJson() {
        return """
                {
                  "settings": {
                    "index": {
                      "number_of_shards": 1,
                      "number_of_replicas": 0
                    }
                  },
                  "mappings": {
                    "properties": {
                      "edgeId":     { "type": "keyword" },
                      "srcType":    { "type": "keyword" },
                      "srcId":      { "type": "long" },
                      "relType":    { "type": "keyword" },
                      "dstType":    { "type": "keyword" },
                      "dstId":      { "type": "long" },
                      "weight":     { "type": "integer" },
                      "source":     { "type": "keyword" },
                      "status":     { "type": "integer" },
                      "updateTime": { "type": "long" }
                    }
                  }
                }
                """;
    }

    /**
     * 派生边文档幂等 _id（PRD §16.4 示例口径）：{@code {srcType}:{srcId}:{relType}:{dstType}:{dstId}}。
     *
     * @param edge 边文档
     * @return 全局唯一 edgeId（同 src 同关系同目标只存在一条边）
     */
    static String edgeId(KgEdgeDoc edge) {
        return edge.srcType() + ":" + edge.srcId() + ":" + edge.relType()
                + ":" + edge.dstType() + ":" + edge.dstId();
    }
}
