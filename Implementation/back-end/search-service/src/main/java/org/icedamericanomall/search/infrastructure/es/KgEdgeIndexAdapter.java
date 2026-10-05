package org.icedamericanomall.search.infrastructure.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
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
import org.icedamericanomall.search.domain.gateway.KgEdgeIndexGateway;
import org.icedamericanomall.search.domain.model.kg.KgEdgeDoc;
import org.icedamericanomall.search.domain.model.kg.KgNeighborHit;
import org.icedamericanomall.search.domain.model.kg.KgNodeRef;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code kg_edges} 边索引的低层读写组件 —— 建索引 / 按 src 清边 / 批量写 / 邻居 terms 检索。
 *
 * <p>结构镜像 {@link KnowledgeVectorIndexAdapter}（低层 {@link ElasticsearchClient}，
 * 索引 mapping 由 {@link KgEdgeMapping} 生成）。仅 {@code search.elasticsearch.enabled=true} 时装配。
 *
 * <p>幂等口径：doc _id = {@code srcType:srcId:relType:dstType:dstId}，且调用方写入前先
 * {@link #deleteBySrc(String, Long)} —— 双重保证重复摄取不产生漂移 doc。
 *
 * <p>失败口径（PRD §16.8.1，KG 纯旁路）：写路径 fail-loud（{@code BizException}，由
 * item-service 捕获降级）；读路径 fail-open（异常按「无命中」返回空列表）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "true")
public class KgEdgeIndexAdapter implements KgEdgeIndexGateway {

    /** 已被其它实例抢先建索引的错误特征串（并发建索引无害化，与既有索引同口径）。 */
    private static final String ALREADY_EXISTS_MARKER = "resource_already_exists_exception";

    /** 命中无得分（理论不发生，仅防御）时的兜底评分。 */
    private static final float NO_SCORE = 0.0F;

    private final ElasticsearchClient esClient;

    /**
     * 确保索引存在（不存在则按 {@link KgEdgeMapping} 创建；并发下已被他人创建则静默通过）。
     */
    @Override
    public void ensureIndex() {
        try {
            if (esClient.indices().exists(e -> e.index(KgEdgeMapping.INDEX_NAME)).value()) {
                return;
            }
            esClient.indices().create(c -> c.index(KgEdgeMapping.INDEX_NAME)
                    .withJson(new StringReader(KgEdgeMapping.buildJson())));
            log.info("图边索引已创建: index={}", KgEdgeMapping.INDEX_NAME);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains(ALREADY_EXISTS_MARKER)) {
                log.debug("图边索引已由其它实例创建: index={}", KgEdgeMapping.INDEX_NAME);
                return;
            }
            throw new BizException(ErrorCode.THIRD_PARTY_CONNECT_FAILED,
                    "图边索引创建失败: " + e.getMessage());
        }
    }

    /**
     * 按 src（srcType + srcId）删除该源节点的全部出边（term 精确匹配，refresh 保证写后可见）。
     */
    @Override
    public void deleteBySrc(String srcType, Long srcId) {
        try {
            esClient.deleteByQuery(d -> d
                    .index(KgEdgeMapping.INDEX_NAME)
                    .query(q -> q.bool(b -> b
                            .must(m -> m.term(t -> t.field(KgEdgeMapping.FIELD_SRC_TYPE)
                                    .value(FieldValue.of(srcType))))
                            .must(m -> m.term(t -> t.field(KgEdgeMapping.FIELD_SRC_ID)
                                    .value(FieldValue.of(srcId))))))
                    .refresh(true));
            log.info("图边已按 src 清除: srcType={}, srcId={}", srcType, srcId);
        } catch (Exception e) {
            throw new BizException(ErrorCode.THIRD_PARTY_CONNECT_FAILED,
                    "图边删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量写入边文档（每边一条 doc，_id = edgeId；updateTime 为写入时刻 epoch 毫秒）。
     *
     * @param edges 边集合（空集合为空操作）
     * @throws BizException 任一 doc 写入失败（fail-loud，由调用方降级）
     */
    @Override
    public void indexEdges(List<KgEdgeDoc> edges) {
        if (edges == null || edges.isEmpty()) {
            return;
        }
        long updateTime = System.currentTimeMillis();
        BulkRequest.Builder bulk = new BulkRequest.Builder();
        for (KgEdgeDoc edge : edges) {
            bulk.operations(op -> op.index(idx -> idx
                    .index(KgEdgeMapping.INDEX_NAME)
                    .id(KgEdgeMapping.edgeId(edge))
                    .document(toDocument(edge, updateTime))));
        }
        executeBulk(bulk, edges.size());
        log.info("图边写入完成: count={}", edges.size());
    }

    /**
     * 邻居检索：源节点集合按类型分组 terms（一组一个 should 子句），可选
     * relType/dstType/status 过滤；排序 = _score 降序（ES relevance）再 weight 降序（同分稳定序）。
     *
     * @return 邻居命中列表（无结果/检索失败为空列表，不抛异常）
     */
    @Override
    public List<KgNeighborHit> searchNeighbors(List<KgNodeRef> srcNodes, String relType,
                                               String dstType, Integer status, int limit) {
        if (srcNodes == null || srcNodes.isEmpty()) {
            return List.of();
        }
        try {
            SearchResponse<ObjectNode> response = esClient.search(s -> {
                s.index(KgEdgeMapping.INDEX_NAME).size(limit);
                s.query(buildQuery(srcNodes, relType, dstType, status));
                s.sort(so -> so.score(sc -> sc.order(SortOrder.Desc)));
                s.sort(so -> so.field(f -> f.field(KgEdgeMapping.FIELD_WEIGHT).order(SortOrder.Desc)));
                return s;
            }, ObjectNode.class);
            return toHits(response);
        } catch (Exception e) {
            // 索引不存在（从未摄取过）等同「无命中」，不放大成故障（fail-open）
            log.warn("图邻居检索失败（按无命中处理）: {}", e.getMessage());
            return List.of();
        }
    }

    /** 邻居查询体：按 srcType 分组的 should 组（term srcType + terms srcId）+ 可选 filter。 */
    private static Query buildQuery(List<KgNodeRef> srcNodes, String relType,
                                    String dstType, Integer status) {
        Map<String, List<Long>> idsByType = new LinkedHashMap<>();
        for (KgNodeRef node : srcNodes) {
            idsByType.computeIfAbsent(node.type(), key -> new ArrayList<>()).add(node.id());
        }
        return Query.of(q -> q.bool(b -> {
            for (Map.Entry<String, List<Long>> group : idsByType.entrySet()) {
                b.should(should -> should.bool(inner -> inner
                        .must(m -> m.term(t -> t.field(KgEdgeMapping.FIELD_SRC_TYPE)
                                .value(FieldValue.of(group.getKey()))))
                        .must(m -> m.terms(ts -> ts.field(KgEdgeMapping.FIELD_SRC_ID)
                                .terms(tv -> tv.value(group.getValue().stream()
                                        .map(FieldValue::of).toList()))))));
            }
            b.minimumShouldMatch("1");
            if (relType != null) {
                b.filter(f -> f.term(t -> t.field(KgEdgeMapping.FIELD_REL_TYPE)
                        .value(FieldValue.of(relType))));
            }
            if (dstType != null) {
                b.filter(f -> f.term(t -> t.field(KgEdgeMapping.FIELD_DST_TYPE)
                        .value(FieldValue.of(dstType))));
            }
            if (status != null) {
                b.filter(f -> f.term(t -> t.field(KgEdgeMapping.FIELD_STATUS)
                        .value(FieldValue.of(status))));
            }
            return b;
        }));
    }

    private void executeBulk(BulkRequest.Builder bulk, int expected) {
        try {
            BulkResponse response = esClient.bulk(bulk.build());
            if (response.errors()) {
                long failed = response.items().stream().filter(item -> item.error() != null).count();
                throw new BizException(ErrorCode.THIRD_PARTY_CONNECT_FAILED,
                        "图边部分写入失败: " + failed + "/" + expected);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.THIRD_PARTY_CONNECT_FAILED,
                    "图边写入失败: " + e.getMessage());
        }
    }

    private static Map<String, Object> toDocument(KgEdgeDoc edge, long updateTime) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put(KgEdgeMapping.FIELD_EDGE_ID, KgEdgeMapping.edgeId(edge));
        doc.put(KgEdgeMapping.FIELD_SRC_TYPE, edge.srcType());
        doc.put(KgEdgeMapping.FIELD_SRC_ID, edge.srcId());
        doc.put(KgEdgeMapping.FIELD_REL_TYPE, edge.relType());
        doc.put(KgEdgeMapping.FIELD_DST_TYPE, edge.dstType());
        doc.put(KgEdgeMapping.FIELD_DST_ID, edge.dstId());
        doc.put(KgEdgeMapping.FIELD_WEIGHT, edge.weight());
        doc.put(KgEdgeMapping.FIELD_SOURCE, edge.source());
        doc.put(KgEdgeMapping.FIELD_STATUS, edge.status());
        doc.put(KgEdgeMapping.FIELD_UPDATE_TIME, updateTime);
        return doc;
    }

    private static List<KgNeighborHit> toHits(SearchResponse<ObjectNode> response) {
        List<KgNeighborHit> hits = new ArrayList<>();
        for (Hit<ObjectNode> hit : response.hits().hits()) {
            ObjectNode source = hit.source();
            if (source == null) {
                continue;
            }
            hits.add(new KgNeighborHit(
                    asText(source.get(KgEdgeMapping.FIELD_DST_TYPE)),
                    asLong(source.get(KgEdgeMapping.FIELD_DST_ID)),
                    asText(source.get(KgEdgeMapping.FIELD_REL_TYPE)),
                    asInt(source.get(KgEdgeMapping.FIELD_WEIGHT)),
                    hit.score() == null ? NO_SCORE : hit.score().floatValue()));
        }
        return hits;
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
}
