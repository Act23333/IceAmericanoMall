package org.icedamericanomall.search.infrastructure.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.KnowledgeVectorClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.search.domain.gateway.EmbeddingGateway;
import org.icedamericanomall.search.domain.gateway.ProductSearchGateway;
import org.icedamericanomall.search.domain.model.search.ProductSearchHit;
import org.icedamericanomall.search.domain.model.search.ProductVectorDoc;
import org.icedamericanomall.search.domain.model.search.SearchPage;
import org.icedamericanomall.search.domain.model.search.SearchQuery;
import org.icedamericanomall.search.domain.service.ProductVectorTextBuilder;
import org.icedamericanomall.search.infrastructure.config.EmbeddingProperties;
import org.icedamericanomall.search.infrastructure.persistence.ProductMapper;
import org.icedamericanomall.search.infrastructure.persistence.ProductPO;
import org.icedamericanomall.search.infrastructure.persistence.SkuMapper;
import org.icedamericanomall.search.infrastructure.persistence.SkuPO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ElasticSearch 检索网关 —— {@code search.elasticsearch.enabled=true} 时激活。
 * ES 7.17 + IK 中文分词，NativeQuery + MatchQuery；kNN 向量检索支持。
 *
 * <p><b>向量写入（I47 / reindex，ADR-046）</b>：索引按 kNN mapping 创建（{@link ProductVectorMapping}）；
 * I47 幂等覆盖写为增量主路径，reindex 全量兜底（逐商品生成向量，单商品失败降级无向量文档）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "true")
public class EsProductSearchGateway implements ProductSearchGateway {

    /** 在售状态（与 DB 网关同口径）。 */
    private static final int STATUS_ON_SALE = 1;

    /** 商品索引名（契约常量，reindex / I28 / I47 共用）。 */
    private static final String INDEX_PRODUCTS = KnowledgeVectorClient.INDEX_PRODUCTS;

    /** 并发建索引时被其它实例抢先的错误特征串（无害化，与知识库索引同口径）。 */
    private static final String ALREADY_EXISTS_MARKER = "resource_already_exists_exception";

    private final ElasticsearchOperations esOps;
    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final ElasticsearchClient esClient;
    private final EmbeddingProperties embeddingProperties;
    private final EmbeddingGateway embeddingGateway;

    @Override
    public SearchPage<ProductSearchHit> search(SearchQuery query) {
        var bool = new BoolQuery.Builder();

        if (query.hasKeyword()) {
            bool.should(Query.of(q -> q.match(MatchQuery.of(m -> m.field("name").query(query.keyword())))));
            bool.should(Query.of(q -> q.match(MatchQuery.of(m -> m.field("description").query(query.keyword())))));
            bool.minimumShouldMatch("1");
        } else {
            bool.must(Query.of(q -> q.matchAll(m -> m)));
        }

        if (query.categoryId() != null) {
            bool.filter(Query.of(q -> q.term(t -> t.field("categoryId").value(query.categoryId()))));
        }

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(Query.of(q -> q.bool(bool.build())))
                .withPageable(PageRequest.of(query.page() - 1, query.size()))
                .build();

        SearchHits<ProductDocument> hits = esOps.search(nativeQuery, ProductDocument.class);

        List<ProductSearchHit> records = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(EsProductSearchGateway::toHit)
                .toList();
        return new SearchPage<>(records, hits.getTotalHits(), query.page(), query.size());
    }

    @Override
    public List<ProductSearchHit> vectorSearch(double[] embedding, int size) {
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

        SearchHits<ProductDocument> hits = esOps.search(query, ProductDocument.class);
        return hits.getSearchHits().stream()
                .map(hit -> toHit(hit.getContent(), hit.getScore()))
                .toList();
    }

    /**
     * ES 全量重建（全量兜底，ADR-046 §5）：按 kNN mapping 重建并逐商品生成向量；
     * Embedding 未配置整体跳过；单商品失败降级为无向量文档，不中断整批。
     */
    @Override
    public Map<String, Object> reindex() {
        List<ProductPO> products = productMapper.selectList(
                new LambdaQueryWrapper<ProductPO>().eq(ProductPO::getStatus, STATUS_ON_SALE));
        Map<Long, Integer> priceMap = loadMinPrices(products);
        boolean vectorEnabled = embeddingGateway.isConfigured();
        if (!vectorEnabled) {
            log.info("ES reindex: Embedding 未配置，本次重建不带向量（关键词检索不受影响）");
        }

        try {
            recreateIndexWithVectorMapping();
            int vectorFailures = 0;
            BulkRequest.Builder bulk = new BulkRequest.Builder();
            for (ProductPO p : products) {
                float[] embedding = vectorEnabled ? embedQuietly(p) : null;
                vectorFailures += vectorEnabled && embedding == null ? 1 : 0;
                bulk.operations(op -> op.index(idx -> idx.index(INDEX_PRODUCTS)
                        .id(String.valueOf(p.getId())).document(toDocMap(p, priceMap, embedding))));
            }
            var resp = esClient.bulk(bulk.build());
            long errors = resp.items().stream().filter(i -> i.error() != null).count();
            log.info("ES reindex complete: {} products, {} errors, vectorEnabled={}, vectorFailures={}",
                    products.size(), errors, vectorEnabled, vectorFailures);
            return Map.of("status", "ok", "count", products.size(), "esEnabled", true,
                    "vectorEnabled", vectorEnabled, "vectorFailures", vectorFailures);
        } catch (Exception e) {
            log.error("ES reindex failed", e);
            return Map.of("status", "error", "message", e.getMessage(), "count", products.size());
        }
    }

    @Override
    public void upsertVectorDoc(ProductVectorDoc doc) {
        ensureVectorIndex();
        esOps.save(toDocument(doc));
    }

    @Override
    public Optional<double[]> findVectorById(Long productId) {
        try {
            ProductDocument doc = esOps.get(String.valueOf(productId), ProductDocument.class);
            return Optional.ofNullable(doc).map(ProductDocument::getEmbedding);
        } catch (Exception e) {
            // 索引不存在/读取失败等同「无向量」（fail-open），仅影响相似检索的空结果
            log.warn("商品向量读取失败（按无向量处理）: productId={}, 原因: {}", productId, e.getMessage());
            return Optional.empty();
        }
    }

    /** 确保 products 索引存在（不存在按 kNN mapping 创建，并发被抢先静默通过；已有索引不变更）。 */
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

    /** 删除并按 kNN mapping 重建 {@code products} 索引（reindex 全量兜底路径）。 */
    private void recreateIndexWithVectorMapping() {
        try {
            esClient.indices().delete(d -> d.index(INDEX_PRODUCTS));
        } catch (Exception ignored) {
            // 索引不存在时删除报错可忽略，随后重建
        }
        createIndexWithVectorMapping();
    }

    /** 按 {@link ProductVectorMapping} 创建索引（kNN 开关 + dense_vector，dims 同源 Embedding 配置）。 */
    private void createIndexWithVectorMapping() {
        String mapping = ProductVectorMapping.buildJson(embeddingProperties.getDims());
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

    /** 在售 SKU 最低价（按商品分组取最小，与 DB 网关同口径）。 */
    private Map<Long, Integer> loadMinPrices(List<ProductPO> products) {
        Map<Long, Integer> priceMap = new HashMap<>();
        for (ProductPO p : products) {
            List<SkuPO> skus = skuMapper.selectList(
                    new LambdaQueryWrapper<SkuPO>()
                            .eq(SkuPO::getProductId, p.getId())
                            .eq(SkuPO::getStatus, STATUS_ON_SALE));
            priceMap.put(p.getId(), skus.stream().mapToInt(SkuPO::getPrice).min().orElse(0));
        }
        return priceMap;
    }

    /** 单商品向量化（fail-open）：失败记 WARN 返回 null（写入无向量文档），不中断整批。 */
    private float[] embedQuietly(ProductPO product) {
        String text = ProductVectorTextBuilder.build(
                product.getName(), product.getDescription(), product.getBrand());
        try {
            return embeddingGateway.embed(text);
        } catch (Exception e) {
            log.warn("商品向量生成失败（本次重建该商品无向量）: productId={}, name={}, 原因: {}",
                    product.getId(), product.getName(), e.getMessage());
            return null;
        }
    }

    /** 商品 PO → ES doc map（embedding 为 null 时不落该字段，即无向量文档）。包内可见以便单测。 */
    static Map<String, Object> toDocMap(ProductPO p, Map<Long, Integer> priceMap, float[] embedding) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", p.getId());
        doc.put("productId", p.getProductId());
        doc.put("name", p.getName());
        doc.put("description", p.getDescription());
        doc.put("brand", p.getBrand());
        doc.put("categoryId", p.getCategoryId());
        doc.put("price", priceMap.getOrDefault(p.getId(), 0));
        doc.put("mainImage", p.getMainImage());
        doc.put("soldCount", p.getSoldCount());
        if (embedding != null) {
            doc.put(ProductVectorMapping.VECTOR_FIELD, toFloatList(embedding));
        }
        return doc;
    }

    /** 领域向量文档 → ES 文档（_id = 商品 id，幂等覆盖；展示字段一并以入参为准）。 */
    private static ProductDocument toDocument(ProductVectorDoc doc) {
        ProductDocument document = new ProductDocument();
        document.setId(doc.productId());
        document.setProductId(String.valueOf(doc.productId()));
        document.setName(doc.name());
        document.setDescription(doc.description());
        document.setBrand(doc.brand());
        document.setCategoryId(doc.categoryId());
        document.setMainImage(doc.mainImage());
        document.setPrice(doc.price());
        document.setSoldCount(doc.soldCount());
        document.setEmbedding(doc.embedding());
        return document;
    }

    private static ProductSearchHit toHit(ProductDocument doc) {
        return toHit(doc, null);
    }

    private static ProductSearchHit toHit(ProductDocument doc, Float score) {
        return new ProductSearchHit(doc.getId(), doc.getProductId(), doc.getCategoryId(), doc.getName(),
                doc.getDescription(), doc.getBrand(), doc.getMainImage(), doc.getPrice(), doc.getSoldCount(),
                score == null ? null : score.doubleValue());
    }

    private static List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float f : array) {
            list.add(f);
        }
        return list;
    }
}
