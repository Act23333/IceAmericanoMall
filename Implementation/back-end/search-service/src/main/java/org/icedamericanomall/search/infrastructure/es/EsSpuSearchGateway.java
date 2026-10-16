package org.icedamericanomall.search.infrastructure.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.ItemSearchClient;
import org.icedamericanomall.client.KnowledgeVectorClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.SpuSearchItemCO;
import org.icedamericanomall.dto.SpuSearchPageCO;
import org.icedamericanomall.search.domain.gateway.EmbeddingGateway;
import org.icedamericanomall.search.domain.gateway.SpuSearchGateway;
import org.icedamericanomall.search.domain.model.search.SpuSearchHit;
import org.icedamericanomall.search.domain.model.search.SpuVectorDoc;
import org.icedamericanomall.search.domain.model.search.SearchFacets;
import org.icedamericanomall.search.domain.model.search.SearchPage;
import org.icedamericanomall.search.domain.model.search.SearchQuery;
import org.icedamericanomall.search.domain.service.SpuVectorTextBuilder;
import org.icedamericanomall.search.infrastructure.config.EmbeddingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ElasticSearch 检索网关 —— {@code search.elasticsearch.enabled=true} 时激活。
 * ES 8.18 客户端 + IK 中文分词，NativeQuery + MatchQuery；kNN 向量检索支持。
 * 查询/排序/facet 装配见 {@link SpuSearchRequestBuilder}，文档转换见 {@link SpuEsDocuments}。
 *
 * <p><b>向量写入（I47 / reindex，ADR-046）</b>：索引按 kNN mapping 创建（{@link SpuVectorMapping}）；
 * I47 幂等覆盖写为增量主路径，reindex 全量兜底（逐商品生成向量，单商品失败降级无向量文档）。
 *
 * <p><b>数据面（ADR-052）</b>：reindex 的商品数据与 publishTime 回填经 {@link ItemSearchClient}
 * 从 item-service 内部检索端点获取（原直读 {@code spu}/{@code sku} 镜像表的实现已退出）
 * ——拉取改为分页循环（{@code REINDEX_PAGE_SIZE}），不再一次性全量载入内存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "true")
public class EsSpuSearchGateway implements SpuSearchGateway {

    /** 商品索引名（契约常量，reindex / I28 / I47 共用）。 */
    private static final String INDEX_PRODUCTS = KnowledgeVectorClient.INDEX_PRODUCTS;

    /** 并发建索引时被其它实例抢先的错误特征串（无害化，与知识库索引同口径）。 */
    private static final String ALREADY_EXISTS_MARKER = "resource_already_exists_exception";

    /** reindex 数据拉取页长（item 内部导出端点分页循环，ADR-052）。 */
    private static final int REINDEX_PAGE_SIZE = 500;

    private final ElasticsearchOperations esOps;
    private final ElasticsearchClient esClient;
    private final EmbeddingProperties embeddingProperties;
    private final EmbeddingGateway embeddingGateway;
    private final ItemSearchClient itemSearchClient;

    @Override
    public SearchPage<SpuSearchHit> search(SearchQuery query) {
        NativeQuery nativeQuery = SpuSearchRequestBuilder.buildNativeQuery(query);
        SearchHits<SpuDocument> hits = esOps.search(nativeQuery, SpuDocument.class);

        List<SpuSearchHit> records = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(SpuEsDocuments::toHit)
                .toList();
        return new SearchPage<>(records, hits.getTotalHits(), query.page(), query.size());
    }

    /**
     * facet 聚合（SE6）—— 品牌/固定价格档位计数（keyword/categoryId 过滤前分布）。
     * 聚合失败降级为空 facet（facet 是辅助过滤 UI，不阻断 C 端搜索，异常不透出）。
     */
    @Override
    public SearchFacets facets(SearchQuery query) {
        try {
            SearchResponse<SpuDocument> response = esClient.search(
                    SpuSearchRequestBuilder.buildFacetsRequest(query), SpuDocument.class);
            return SpuSearchRequestBuilder.parseFacets(response.aggregations());
        } catch (Exception e) {
            log.warn("facet 聚合失败（辅助 UI 降级为空对象，不阻断搜索）: keyword={}, 原因: {}",
                    query.keyword(), e.getMessage());
            return SearchFacets.empty();
        }
    }

    @Override
    public List<SpuSearchHit> vectorSearch(double[] embedding, int size) {
        float[] floatEmbedding = new float[embedding.length];
        for (int i = 0; i < embedding.length; i++) {
            floatEmbedding[i] = (float) embedding[i];
        }

        Query knn = Query.of(q -> q.knn(KnnQuery.of(k -> k
                .field("embedding")
                .queryVector(toFloatList(floatEmbedding))
                .k(size)
                .numCandidates(size * 2))));

        NativeQuery query = NativeQuery.builder()
                .withQuery(knn)
                .withPageable(PageRequest.of(0, size))
                .build();

        SearchHits<SpuDocument> hits = esOps.search(query, SpuDocument.class);
        return hits.getSearchHits().stream()
                .map(hit -> SpuEsDocuments.toHit(hit.getContent(), hit.getScore()))
                .toList();
    }

    /**
     * ES 全量重建（全量兜底，ADR-046 §5）：按 kNN mapping 重建并逐商品生成向量；
     * Embedding 未配置整体跳过；单商品失败降级为无向量文档，不中断整批。
     * 商品数据经 item-service 内部导出端点分页拉取（ADR-052，不再直读镜像表）。
     */
    @Override
    public Map<String, Object> reindex() {
        List<SpuSearchItemCO> spus = fetchOnSaleSpus();
        boolean vectorEnabled = embeddingGateway.isConfigured();
        if (!vectorEnabled) {
            log.info("ES reindex: Embedding 未配置，本次重建不带向量（关键词检索不受影响）");
        }

        try {
            recreateIndexWithVectorMapping();
            int vectorFailures = 0;
            BulkRequest.Builder bulk = new BulkRequest.Builder();
            for (SpuSearchItemCO p : spus) {
                float[] embedding = vectorEnabled ? embedQuietly(p) : null;
                vectorFailures += vectorEnabled && embedding == null ? 1 : 0;
                bulk.operations(op -> op.index(idx -> idx.index(INDEX_PRODUCTS)
                        .id(String.valueOf(p.getId())).document(SpuEsDocuments.toDocMap(p, embedding))));
            }
            var resp = esClient.bulk(bulk.build());
            long errors = resp.items().stream().filter(i -> i.error() != null).count();
            log.info("ES reindex complete: {} spus, {} errors, vectorEnabled={}, vectorFailures={}",
                    spus.size(), errors, vectorEnabled, vectorFailures);
            return Map.of("status", "ok", "count", spus.size(), "esEnabled", true,
                    "vectorEnabled", vectorEnabled, "vectorFailures", vectorFailures);
        } catch (Exception e) {
            log.error("ES reindex failed", e);
            return Map.of("status", "error", "message", e.getMessage(), "count", spus.size());
        }
    }

    @Override
    public void upsertVectorDoc(SpuVectorDoc doc) {
        ensureVectorIndex();
        SpuDocument document = SpuEsDocuments.toDocument(doc);
        applyPublishTimeQuietly(document);
        esOps.save(document);
    }

    @Override
    public Optional<double[]> findVectorById(Long spuId) {
        try {
            SpuDocument doc = esOps.get(String.valueOf(spuId), SpuDocument.class);
            return Optional.ofNullable(doc).map(SpuDocument::getEmbedding);
        } catch (Exception e) {
            // 索引不存在/读取失败等同「无向量」（fail-open），仅影响相似检索的空结果
            log.warn("商品向量读取失败（按无向量处理）: spuId={}, 原因: {}", spuId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 在售商品全量拉取（ADR-052：item 域数据面出口）——分页循环拉取，
     * 单页为空或累计达到总数即终止（防御调用方 total 漂移导致死循环）。
     */
    private List<SpuSearchItemCO> fetchOnSaleSpus() {
        List<SpuSearchItemCO> all = new ArrayList<>();
        int page = 1;
        while (true) {
            SpuSearchPageCO co = itemSearchClient.exportPage(page, REINDEX_PAGE_SIZE);
            if (co == null || co.getRecords() == null || co.getRecords().isEmpty()) {
                break;
            }
            all.addAll(co.getRecords());
            if (all.size() >= co.getTotal()) {
                break;
            }
            page++;
        }
        return all;
    }

    /**
     * I47 覆盖写补齐 publishTime（SE1 newest 排序）—— 摄取契约（{@code SpuVectorUpsertCO}）
     * 无此字段，整文档覆盖写会丢列，故经 item 内部端点回填（ADR-052）；
     * 缺失/读取失败按无值处理（fail-open，仅影响 newest 排序，不阻断摄取主流程）。
     */
    private void applyPublishTimeQuietly(SpuDocument document) {
        try {
            SpuSearchItemCO item = itemSearchClient.detail(Long.valueOf(document.getId()));
            if (item != null && item.getPublishTime() != null) {
                document.setPublishTime(item.getPublishTime());
            }
        } catch (Exception e) {
            log.warn("商品 publishTime 回填失败（按缺失处理）: spuId={}, 原因: {}",
                    document.getId(), e.getMessage());
        }
    }

    /** 确保 spus 索引存在（不存在按 kNN mapping 创建，并发被抢先静默通过；已有索引不变更）。 */
    private void ensureVectorIndex() {
        try {
            if (esClient.indices().exists(e -> e.index(INDEX_PRODUCTS)).value()) {
                return;
            }
        } catch (Exception e) {
            throw new BizException(ErrorCode.THIRD_PARTY_CONNECT_FAILED,
                    "商品向量索引探测失败: " + e.getMessage());
        }
        createIndexWithVectorMapping();
    }

    /** 删除并按 kNN mapping 重建 {@code spus} 索引（reindex 全量兜底路径）。 */
    private void recreateIndexWithVectorMapping() {
        try {
            esClient.indices().delete(d -> d.index(INDEX_PRODUCTS));
        } catch (Exception ignored) {
            // 索引不存在时删除报错可忽略，随后重建
        }
        createIndexWithVectorMapping();
    }

    /** 按 {@link SpuVectorMapping} 创建索引（kNN 开关 + dense_vector，dims 同源 Embedding 配置）。 */
    private void createIndexWithVectorMapping() {
        String mapping = SpuVectorMapping.buildJson(embeddingProperties.getDims());
        try {
            esClient.indices().create(c -> c.index(INDEX_PRODUCTS).withJson(new StringReader(mapping)));
            log.info("商品向量索引已创建: index={}, dims={}", INDEX_PRODUCTS, embeddingProperties.getDims());
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains(ALREADY_EXISTS_MARKER)) {
                log.debug("商品向量索引已由其它实例创建: index={}", INDEX_PRODUCTS);
                return;
            }
            throw new BizException(ErrorCode.THIRD_PARTY_CONNECT_FAILED,
                    "商品向量索引创建失败: " + e.getMessage());
        }
    }

    /** 单商品向量化（fail-open）：失败记 WARN 返回 null（写入无向量文档），不中断整批。 */
    private float[] embedQuietly(SpuSearchItemCO spu) {
        String text = SpuVectorTextBuilder.build(
                spu.getName(), spu.getDescription(), spu.getBrand());
        try {
            return embeddingGateway.embed(text);
        } catch (Exception e) {
            log.warn("商品向量生成失败（本次重建该商品无向量）: spuId={}, name={}, 原因: {}",
                    spu.getId(), spu.getName(), e.getMessage());
            return null;
        }
    }

    private static List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float f : array) {
            list.add(f);
        }
        return list;
    }
}
