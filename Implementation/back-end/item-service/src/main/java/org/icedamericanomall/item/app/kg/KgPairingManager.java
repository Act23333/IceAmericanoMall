package org.icedamericanomall.item.app.kg;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.KgClient;
import org.icedamericanomall.dto.KgNeighborCO;
import org.icedamericanomall.item.adapter.response.PairingCardVO;
import org.icedamericanomall.item.adapter.response.RecommendProductVO;
import org.icedamericanomall.item.app.ProductManager;
import org.icedamericanomall.item.app.SkuManager;
import org.icedamericanomall.item.domain.enums.ProductStatusEnum;
import org.icedamericanomall.item.domain.model.kg.KgSchema;
import org.icedamericanomall.item.domain.model.product.Product;
import org.icedamericanomall.item.domain.repository.ProductRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 搭配推荐卡编排（应用层 · C 端，F15 P35，契约 §4.4.x V5.16；D4-B 实体驱动变体：
 * 图召回为主、向量兜底）。
 *
 * <p><b>组装流水线（US-KG-1/2/3）</b>：锚点商品在售校验（不在售 → 空态 NONE）→
 * I58 取锚点类目的启用 PAIR_WITH 邻居（Phase 1 一跳：锚点类目 → 邻居类目）→
 * 逐邻居类目查本域在售商品（销量降序）→ 全局按「规则权重降序 + 销量降序」排序、
 * 排除自身、截断 limit → source=KG；无规则命中 → 回退 P27 向量相似管道
 * （source=FALLBACK，复用其排除自身/恒过滤在售/降级空列表语义）；仍无 → source=NONE。
 *
 * <p><b>fail-open（PRD §16.8.1 最高优先级）</b>：client 降级已兜底空列表，本类再防御性
 * 兜底任意未预期异常（{@code ProductManager#similarProducts} 同款口径）——任何环节失效
 * 返回空态卡，详情页主流程零感知。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KgPairingManager {

    /** 搭配卡缺省条数（P35 契约：缺省 6）。 */
    private static final int DEFAULT_CARD_SIZE = 6;

    /** 搭配卡单次上限（P35 契约：≤10 归一）。 */
    private static final int MAX_CARD_SIZE = 10;

    /** 图查询跳数（Phase 1 搭配链路为一跳：锚点类目 → PAIR_WITH → 邻居类目）。 */
    private static final int NEIGHBOR_DEPTH = 1;

    private final KgClient kgClient;
    private final ProductRepository productRepository;
    private final SkuManager skuManager;
    private final ProductManager productManager;

    /**
     * 搭配推荐卡（P35，全链路 fail-open，恒不抛错）。
     *
     * @param productId 锚点商品 id
     * @param limit     期望条数（null/非正 → 缺省 6，&gt;10 归一为 10）
     * @return 搭配卡（KG / FALLBACK / NONE 三态，items 恒非 null）
     */
    public PairingCardVO pairing(Long productId, Integer limit) {
        try {
            return assemblePairing(productId, normalizeSize(limit));
        } catch (Exception e) {
            log.warn("搭配卡组装失败，返回空态（fail-open，US-KG-9）: productId={}, limit={}",
                    productId, limit, e);
            return PairingCardVO.none();
        }
    }

    // ==================== 内部方法 ====================

    /** 组装主流水线：在售校验 → 图召回 → 向量兜底 → 空态。 */
    private PairingCardVO assemblePairing(Long productId, int size) {
        Product product = productRepository.findById(productId);
        if (product == null || !isOnShelf(product)) {
            return PairingCardVO.none();
        }
        List<RecommendProductVO> items = itemsFromGraph(productId, product.getCategoryId(), size);
        if (!items.isEmpty()) {
            return PairingCardVO.kg(items);
        }
        List<RecommendProductVO> fallback = productManager.similarProducts(productId, size);
        return fallback.isEmpty() ? PairingCardVO.none() : PairingCardVO.fallback(fallback);
    }

    /**
     * 图召回组装：I58 邻居（status 传 null = 缺省仅启用边——规则启停即时生效的查询侧落点）→
     * 逐邻居类目取在售候选 → 全局排序截断 → 批量回填在售最低价（一次聚合，无 N+1）。
     */
    private List<RecommendProductVO> itemsFromGraph(Long productId, Long categoryId, int size) {
        if (categoryId == null) {
            return List.of();
        }
        List<KgNeighborCO> neighbors = kgClient.neighbors(KgSchema.NODE_CATEGORY, categoryId,
                KgSchema.REL_PAIR_WITH, KgSchema.NODE_CATEGORY, NEIGHBOR_DEPTH, null);
        if (neighbors == null || neighbors.isEmpty()) {
            return List.of();
        }
        List<Candidate> candidates = collectCandidates(productId, neighbors, size);
        candidates.sort(KgPairingManager::byWeightThenSales);
        List<Product> selected = candidates.stream()
                .limit(size)
                .map(Candidate::product)
                .toList();
        Map<Long, Integer> minPriceMap = skuManager.getMinPriceMapByProductIds(
                selected.stream().map(Product::getId).toList());
        return selected.stream()
                .map(product -> toCard(product, minPriceMap.get(product.getId())))
                .toList();
    }

    /**
     * 逐邻居类目收集在售候选（排除锚点自身；类目内取销量 top-{@code size}——
     * 同权重下未进自家 top-size 的商品必不入全局 top-size，截断无损）。
     */
    private List<Candidate> collectCandidates(Long productId, List<KgNeighborCO> neighbors, int size) {
        Map<Long, Candidate> unique = new LinkedHashMap<>();
        for (KgNeighborCO neighbor : neighbors) {
            if (neighbor == null || neighbor.getDstId() == null
                    || !KgSchema.NODE_CATEGORY.equals(neighbor.getDstType())) {
                continue;
            }
            int weight = neighbor.getWeight() == null ? 0 : neighbor.getWeight();
            for (Product product : productRepository.pageOnSale(null, neighbor.getDstId(),
                    null, "sales", 1, size).getRecords()) {
                if (product.getId().equals(productId) || !isOnShelf(product)) {
                    // 排除自身 + 恒过滤在售（下架/删除剔除，P27 同款防御口径）
                    continue;
                }
                unique.putIfAbsent(product.getId(), new Candidate(product, weight));
            }
        }
        return new ArrayList<>(unique.values());
    }

    /** 全局排序：规则权重降序 → 销量降序 → id 升序（同分稳定序）。 */
    private static int byWeightThenSales(Candidate a, Candidate b) {
        int byWeight = Integer.compare(b.weight(), a.weight());
        if (byWeight != 0) {
            return byWeight;
        }
        int bySales = Integer.compare(salesOf(b.product()), salesOf(a.product()));
        if (bySales != 0) {
            return bySales;
        }
        return Long.compare(a.product().getId(), b.product().getId());
    }

    private static int salesOf(Product product) {
        return product.getSoldCount() == null ? 0 : product.getSoldCount();
    }

    /** 命中商品 → 搭配卡（字段集对齐 P27 相似卡：展示价 = 在售 SKU 最低价，分）。 */
    private RecommendProductVO toCard(Product product, Integer minPrice) {
        RecommendProductVO vo = new RecommendProductVO();
        vo.setProductId(product.getId());
        vo.setName(product.getName());
        vo.setMainImage(product.getMainImage());
        vo.setPrice(minPrice);
        vo.setSoldCount(product.getSoldCount());
        return vo;
    }

    /** size 归一（P35 契约：缺省 6、≤10；非正按缺省处理）。 */
    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_CARD_SIZE;
        }
        return Math.min(size, MAX_CARD_SIZE);
    }

    private boolean isOnShelf(Product product) {
        return product.getStatus() != null
                && product.getStatus() == ProductStatusEnum.ON_SHELF.getCode();
    }

    /**
     * 搭配候选（类目内销量序取数 + 全局权重/销量重排的中间形态）。
     *
     * @param product 在售商品聚合
     * @param weight  所经 PAIR_WITH 规则权重
     */
    private record Candidate(Product product, int weight) {
    }
}
