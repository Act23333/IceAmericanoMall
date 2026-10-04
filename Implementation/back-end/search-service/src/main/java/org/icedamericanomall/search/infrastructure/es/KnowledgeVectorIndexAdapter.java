package org.icedamericanomall.search.infrastructure.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.search.domain.gateway.KnowledgeVectorIndexGateway;
import org.icedamericanomall.search.domain.model.search.KnowledgeChunkHit;
import org.icedamericanomall.search.domain.service.DocumentChunker;
import org.icedamericanomall.search.infrastructure.config.EmbeddingProperties;
import org.icedamericanomall.search.infrastructure.config.KnowledgeVectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code knowledge_vectors} 索引的低层读写组件 —— 建索引 / 批量写 / 按 knowledgeId 删 / kNN 检索。
 *
 * <p>与关键词追踪适配器同类：把「同一份数据契约只在一处定义」的引擎细节
 * 收拢到一个组件，供 Service 编排调用。仅 {@code search.elasticsearch.enabled=true} 时装配。
 *
 * <p>采用低层 {@link ElasticsearchClient}（而非 Spring Data ES 注解映射）的原因：索引名与
 * 维度是运行时配置，注解方案会把二者钉死为编译期常量。索引 mapping 由
 * {@link KnowledgeVectorMapping} 生成。
 *
 * <p>幂等口径：写入前调用方先 {@link #deleteByKnowledgeId(Long)}，且 doc _id 固定为
 * {@code {knowledgeId}_{chunkIndex}}，双重保证重复摄取不产生漂移 doc。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "true")
public class KnowledgeVectorIndexAdapter implements KnowledgeVectorIndexGateway {

    /** kNN 候选倍数（候选 = Top-K × 该倍数，至少 50）。 */
    private static final int CANDIDATE_MULTIPLIER = 2;

    /** kNN 候选下限。 */
    private static final int MIN_NUM_CANDIDATES = 50;

    /** 已被其它实例抢先建索引的错误特征串（并发建索引无害化）。 */
    private static final String ALREADY_EXISTS_MARKER = "resource_already_exists_exception";

    /** 检索返回的 source 字段白名单（不回传 vector 本身）。 */
    private static final List<String> SOURCE_FIELDS =
            List.of("knowledgeId", "chunkIndex", "sellerId", "title", "text");

    private final ElasticsearchClient esClient;
    private final KnowledgeVectorProperties indexProperties;
    private final EmbeddingProperties embeddingProperties;

    /**
     * 确保索引存在（不存在则按 dims 建索引；并发下已被他人创建则静默通过）。
     */
    public void ensureIndex() {
        String index = indexProperties.getIndexName();
        String mapping = KnowledgeVectorMapping.buildJson(embeddingProperties.getDims());
        // ES 客户端各方法声明 IOException（transport I/O），故整段包在 try 内，统一转为业务异常
        try {
            if (esClient.indices().exists(e -> e.index(index)).value()) {
                return;
            }
            esClient.indices().create(c -> c.index(index).withJson(new StringReader(mapping)));
            log.info("知识库向量索引已创建: index={}, dims={}", index, embeddingProperties.getDims());
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains(ALREADY_EXISTS_MARKER)) {
                log.debug("知识库向量索引已由其它实例创建: index={}", index);
                return;
            }
            throw new BizException(ErrorCode.THIRD_PARTY_CONNECT_FAILED,
                    "知识库向量索引创建失败: " + e.getMessage());
        }
    }

    /**
     * 按 knowledgeId 删除该条目全部 chunk（term 精确匹配；refresh 保证后续写入/检索可见）。
     *
     * @param knowledgeId 知识条目业务ID
     */
    public void deleteByKnowledgeId(Long knowledgeId) {
        try {
            esClient.deleteByQuery(d -> d
                    .index(indexProperties.getIndexName())
                    .query(q -> q.term(t -> t.field("knowledgeId").value(FieldValue.of(knowledgeId))))
                    .refresh(true));
            log.info("知识库向量已删除: knowledgeId={}", knowledgeId);
        } catch (Exception e) {
            throw new BizException(ErrorCode.THIRD_PARTY_CONNECT_FAILED,
                    "知识库向量删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量写入 chunk（每 chunk 一条 doc，_id = {@code {knowledgeId}_{chunkIndex}}）。
     *
     * @param knowledgeId 知识条目业务ID
     * @param sellerId    归属商家ID
     * @param chunks      分块结果
     * @param vectors     与 chunks 一一对应的向量
     * @throws BizException 向量条数与分块数不一致，或任一 doc 写入失败（fail-loud，由调用方降级）
     */
    public void indexChunks(Long knowledgeId, Long sellerId,
                            List<DocumentChunker.Chunk> chunks, List<float[]> vectors) {
        if (chunks.size() != vectors.size()) {
            throw new BizException(ErrorCode.THIRD_PARTY_CONNECT_FAILED,
                    "知识库向量写入失败: chunk 数(" + chunks.size() + ")与向量数(" + vectors.size() + ")不一致");
        }
        BulkRequest.Builder bulk = new BulkRequest.Builder();
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunker.Chunk chunk = chunks.get(i);
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("knowledgeId", knowledgeId);
            doc.put("chunkIndex", i);
            doc.put("sellerId", sellerId);
            doc.put("title", chunk.title());
            doc.put("text", chunk.text());
            doc.put(KnowledgeVectorMapping.VECTOR_FIELD, toFloatList(vectors.get(i)));
            String docId = knowledgeId + "_" + i;
            bulk.operations(op -> op.index(idx -> idx
                    .index(indexProperties.getIndexName())
                    .id(docId)
                    .document(doc)));
        }
        try {
            BulkResponse response = esClient.bulk(bulk.build());
            if (response.errors()) {
                long failed = response.items().stream().filter(item -> item.error() != null).count();
                throw new BizException(ErrorCode.THIRD_PARTY_CONNECT_FAILED,
                        "知识库向量部分写入失败: " + failed + "/" + chunks.size());
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.THIRD_PARTY_CONNECT_FAILED,
                    "知识库向量写入失败: " + e.getMessage());
        }
        log.info("知识库向量写入完成: knowledgeId={}, chunks={}", knowledgeId, chunks.size());
    }

    /**
     * kNN 检索知识 chunk（可按 sellerId 过滤）。
     *
     * @param queryVector 查询向量
     * @param size        Top-K（≤0 时用配置缺省值）
     * @param sellerId    商家过滤（null = 全站）
     * @return 命中列表（无结果为空列表）
     */
    public List<KnowledgeChunkHit> knnSearch(float[] queryVector, int size, Long sellerId) {
        int topK = size > 0 ? size : indexProperties.getDefaultTopK();
        int numCandidates = Math.max(topK * CANDIDATE_MULTIPLIER, MIN_NUM_CANDIDATES);
        List<Float> vector = toFloatList(queryVector);
        try {
            SearchResponse<ObjectNode> response = esClient.search(s -> {
                s.index(indexProperties.getIndexName())
                        .size(topK)
                        .source(sc -> sc.filter(f -> f.includes(SOURCE_FIELDS)));
                s.knn(k -> {
                    k.field(KnowledgeVectorMapping.VECTOR_FIELD)
                            .queryVector(vector)
                            .k(topK)
                            .numCandidates(numCandidates);
                    if (sellerId != null) {
                        k.filter(q -> q.term(t -> t.field("sellerId").value(FieldValue.of(sellerId))));
                    }
                    return k;
                });
                return s;
            }, ObjectNode.class);
            return toHits(response);
        } catch (Exception e) {
            // 索引不存在（从未摄取过）等同于「无命中」，不放大成故障
            log.warn("知识库向量检索失败（按无命中处理）: {}", e.getMessage());
            return List.of();
        }
    }

    private static List<KnowledgeChunkHit> toHits(SearchResponse<ObjectNode> response) {
        List<KnowledgeChunkHit> hits = new ArrayList<>();
        for (Hit<ObjectNode> hit : response.hits().hits()) {
            ObjectNode source = hit.source();
            if (source == null) {
                continue;
            }
            hits.add(new KnowledgeChunkHit(
                    asLong(source.get("knowledgeId")),
                    asInt(source.get("chunkIndex")),
                    asLong(source.get("sellerId")),
                    asText(source.get("title")),
                    asText(source.get("text"))));
        }
        return hits;
    }

    private static List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float value : array) {
            list.add(value);
        }
        return list;
    }

    private static Long asLong(JsonNode node) {
        return node == null || node.isNull() ? null : node.asLong();
    }

    private static Integer asInt(JsonNode node) {
        return node == null || node.isNull() ? null : node.asInt();
    }

    private static String asText(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    /**
     * 检索命中（支撑层内部形状）—— 由 Service 转为对外 VO。
     *
     * @param knowledgeId 知识条目业务ID
     * @param chunkIndex  chunk 序号
     * @param sellerId    归属商家ID
     * @param title       chunk 标题
     * @param text        chunk 正文
     */
}
