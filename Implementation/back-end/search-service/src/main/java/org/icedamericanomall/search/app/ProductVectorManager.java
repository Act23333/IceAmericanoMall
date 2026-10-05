package org.icedamericanomall.search.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.dto.ProductVectorHitCO;
import org.icedamericanomall.dto.ProductVectorUpsertCO;
import org.icedamericanomall.search.domain.gateway.EmbeddingGateway;
import org.icedamericanomall.search.domain.gateway.ProductSearchGateway;
import org.icedamericanomall.search.domain.model.search.ProductSearchHit;
import org.icedamericanomall.search.domain.model.search.ProductVectorDoc;
import org.icedamericanomall.search.domain.service.ProductVectorTextBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 商品向量编排（应用层）—— 摄取 / 相似检索（复用商品向量底座，ADR-046）。
 *
 * <p><b>摄取链路（I47）</b>：商品原文 → 拼接向量化文本（{@link ProductVectorTextBuilder}，
 * {@code name + description} 可选拼 brand 的单一事实源，reindex 同口径）→ {@link EmbeddingGateway}
 * 单向量 → {@link ProductSearchGateway#upsertVectorDoc(ProductVectorDoc)} 幂等覆盖写
 * （productId 幂等键，展示字段一并以入参为准，顺带补齐「日常增量进 ES」缺口）。
 *
 * <p><b>相似检索链路（I48）</b>：取自身向量 → 以自身向量 kNN（Top-K = size + 1）→ 过滤自身
 * → 只出 {@code productId + score}（C 端商品卡组装归 item-service：MySQL 是展示真相源）。
 *
 * <p><b>渐进启用 / 全链路 fail-open（推荐是纯旁路）</b>：
 * <ul>
 *   <li>Embedding 未配置 → 摄取返回 0 并记 WARN，<b>不报错</b>（ES/Embedding 就绪前默认状态）；</li>
 *   <li>Embedding/写入失败 → 抛 BizException（fail-loud），由调用方（item-service）捕获降级为哨兵；</li>
 *   <li>自身无向量（冷启动存量商品）→ 相似检索返回空列表，推荐位可空。</li>
 * </ul>
 *
 * <p>仅 {@code search.elasticsearch.enabled=true} 时装配（依赖 ES 网关实现）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "true")
public class ProductVectorManager {

    /** 相似检索缺省条数（契约 I48：size 缺省 5）。 */
    private static final int DEFAULT_SIMILAR_SIZE = 5;

    /** 相似检索上限（契约 I48：size ≤ 20）。 */
    private static final int MAX_SIMILAR_SIZE = 20;

    /** 命中无得分（理论不发生，仅防御）时的兜底相似度。 */
    private static final double NO_SCORE = 0.0D;

    private final EmbeddingGateway embeddingGateway;
    private final ProductSearchGateway productSearchGateway;

    /**
     * 商品向量摄取：拼接向量化文本 → 单向量 → 幂等覆盖写。
     *
     * @param co 商品原文与展示字段（productId 幂等键）
     * @return 1=已写入向量索引；0=Embedding 未启用（未启用不报错，调用方零感知）
     * @throws org.icedamericanomall.common.exception.BizException 向量化或写入失败（调用方降级为哨兵）
     */
    public int upsert(ProductVectorUpsertCO co) {
        if (!embeddingGateway.isConfigured()) {
            log.warn("商品向量摄取未启用（search.embedding 未配置），跳过: productId={}", co.getProductId());
            return 0;
        }
        float[] vector = embeddingGateway.embed(
                ProductVectorTextBuilder.build(co.getName(), co.getDescription(), co.getBrand()));
        productSearchGateway.upsertVectorDoc(toVectorDoc(co, vector));
        log.info("商品向量已写入: productId={}", co.getProductId());
        return 1;
    }

    /**
     * 相似商品检索：自身向量 kNN，排除自身，按相似度降序（排序由 ES kNN 保证）。
     *
     * @param productId 商品 id（取自身向量）
     * @param size      返回条数（≤0 归一 5，&gt;20 截 20）
     * @return 命中列表（productId + score）；自身无向量/无命中时为空列表
     */
    public List<ProductVectorHitCO> similar(Long productId, Integer size) {
        int topK = normalizeSize(size);
        Optional<double[]> selfVector = productSearchGateway.findVectorById(productId);
        if (selfVector.isEmpty() || selfVector.get().length == 0) {
            return List.of();
        }
        // 多取 1 条兜底「命中含自身被过滤」，补回不足的条数
        List<ProductSearchHit> hits = productSearchGateway.vectorSearch(selfVector.get(), topK + 1);
        return hits.stream()
                .filter(hit -> hit.id() != null && !hit.id().equals(productId))
                .limit(topK)
                .map(ProductVectorManager::toHitCO)
                .toList();
    }

    private static int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIMILAR_SIZE;
        }
        return Math.min(size, MAX_SIMILAR_SIZE);
    }

    /** 契约 CO → 领域向量文档（float → double 对齐 {@code ProductDocument.embedding}）。 */
    private static ProductVectorDoc toVectorDoc(ProductVectorUpsertCO co, float[] vector) {
        double[] embedding = new double[vector.length];
        for (int i = 0; i < vector.length; i++) {
            embedding[i] = vector[i];
        }
        return new ProductVectorDoc(co.getProductId(), co.getName(), co.getDescription(), co.getBrand(),
                co.getCategoryId(), co.getMainImage(), co.getPrice(), co.getSoldCount(), embedding);
    }

    private static ProductVectorHitCO toHitCO(ProductSearchHit hit) {
        ProductVectorHitCO co = new ProductVectorHitCO();
        co.setProductId(hit.id());
        co.setScore(hit.score() == null ? NO_SCORE : hit.score());
        return co;
    }
}
