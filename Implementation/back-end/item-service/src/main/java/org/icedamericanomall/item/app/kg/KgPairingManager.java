package org.icedamericanomall.item.app.kg;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.KgClient;
import org.icedamericanomall.dto.KgNeighborCO;
import org.icedamericanomall.item.adapter.response.PairingCardVO;
import org.icedamericanomall.item.adapter.response.RecommendSpuVO;
import org.icedamericanomall.item.app.SpuManager;
import org.icedamericanomall.item.app.SkuManager;
import org.icedamericanomall.item.domain.enums.SpuStatusEnum;
import org.icedamericanomall.item.domain.model.kg.KgSchema;
import org.icedamericanomall.item.domain.model.spu.Spu;
import org.icedamericanomall.item.domain.repository.SpuRepository;
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
 * 兜底任意未预期异常（{@code SpuManager#similarSpus} 同款口径）——任何环节失效
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
    private final SpuRepository spuRepository;
    private final SkuManager skuManager;
    private final SpuManager spuManager;

    /**
     * 搭配推荐卡（P35，全链路 fail-open，恒不抛错）。
     *
     * @param spuId 锚点 SPU <b>业务键</b>（32-hex，路径变量 {@code {spuId}} 口径，ADR-062 §决策 2）；
     *              形态校验在适配层，本层做业务键 → 技术键解析（同库 {@code uk_spu_id}）
     * @param limit 期望条数（null/非正 → 缺省 6，&gt;10 归一为 10）
     * @return 搭配卡（KG / FALLBACK / NONE 三态，items 恒非 null）
     */
    public PairingCardVO pairing(String spuId, Integer limit) {
        try {
            return assemblePairing(spuId, normalizeSize(limit));
        } catch (Exception e) {
            log.warn("搭配卡组装失败，返回空态（fail-open，US-KG-9）: spuId={}, limit={}",
                    spuId, limit, e);
            return PairingCardVO.none();
        }
    }

    // ==================== 内部方法 ====================

    /** 组装主流水线：业务键 → 技术键 → 在售校验 → 图召回 → 向量兜底 → 空态。 */
    private PairingCardVO assemblePairing(String spuBusinessId, int size) {
        Spu spu = spuRepository.findByBusinessId(spuBusinessId);
        if (spu == null || !isOnShelf(spu)) {
            return PairingCardVO.none();
        }
        Long spuId = spu.getId();
        List<RecommendSpuVO> items = itemsFromGraph(spuId, spu.getCategoryId(), size);
        if (!items.isEmpty()) {
            return PairingCardVO.kg(items);
        }
        // 向量兜底复用 P27 管道（同样以业务键入参，ADR-062 §决策 3）
        List<RecommendSpuVO> fallback = spuManager.similarSpus(spu.getSpuId(), size);
        return fallback.isEmpty() ? PairingCardVO.none() : PairingCardVO.fallback(fallback);
    }

    /**
     * 图召回组装：I58 邻居（status 传 null = 缺省仅启用边——规则启停即时生效的查询侧落点）→
     * 逐邻居类目取在售候选 → 全局排序截断 → 批量回填在售最低价（一次聚合，无 N+1）。
     */
    private List<RecommendSpuVO> itemsFromGraph(Long spuId, Long categoryId, int size) {
        if (categoryId == null) {
            return List.of();
        }
        List<KgNeighborCO> neighbors = kgClient.neighbors(KgSchema.NODE_CATEGORY, categoryId,
                KgSchema.REL_PAIR_WITH, KgSchema.NODE_CATEGORY, NEIGHBOR_DEPTH, null);
        if (neighbors == null || neighbors.isEmpty()) {
            return List.of();
        }
        List<Candidate> candidates = collectCandidates(spuId, neighbors, size);
        candidates.sort(KgPairingManager::byWeightThenSales);
        List<Spu> selected = candidates.stream()
                .limit(size)
                .map(Candidate::spu)
                .toList();
        Map<Long, Integer> minPriceMap = skuManager.getMinPriceMapBySpuIds(
                selected.stream().map(Spu::getId).toList());
        return selected.stream()
                .map(spu -> toCard(spu, minPriceMap.get(spu.getId())))
                .toList();
    }

    /**
     * 逐邻居类目收集在售候选（排除锚点自身；类目内取销量 top-{@code size}——
     * 同权重下未进自家 top-size 的商品必不入全局 top-size，截断无损）。
     */
    private List<Candidate> collectCandidates(Long spuId, List<KgNeighborCO> neighbors, int size) {
        Map<Long, Candidate> unique = new LinkedHashMap<>();
        for (KgNeighborCO neighbor : neighbors) {
            if (neighbor == null || neighbor.getDstId() == null
                    || !KgSchema.NODE_CATEGORY.equals(neighbor.getDstType())) {
                continue;
            }
            int weight = neighbor.getWeight() == null ? 0 : neighbor.getWeight();
            for (Spu spu : spuRepository.pageOnSale(null, neighbor.getDstId(),
                    null, "sales", 1, size).getRecords()) {
                if (spu.getId().equals(spuId) || !isOnShelf(spu)) {
                    // 排除自身 + 恒过滤在售（下架/删除剔除，P27 同款防御口径）
                    continue;
                }
                unique.putIfAbsent(spu.getId(), new Candidate(spu, weight));
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
        int bySales = Integer.compare(salesOf(b.spu()), salesOf(a.spu()));
        if (bySales != 0) {
            return bySales;
        }
        return Long.compare(a.spu().getId(), b.spu().getId());
    }

    private static int salesOf(Spu spu) {
        return spu.getSoldCount() == null ? 0 : spu.getSoldCount();
    }

    /**
     * 命中商品 → 搭配卡（字段集对齐 P27 相似卡：展示价 = 在售 SKU 最低价，分）。
     *
     * <p>{@code spuId} 出 <b>SPU 业务键</b>（32-hex，ADR-062 §决策 3）：与 P27 同一张卡、
     * 同一条回链路径（{@code GET /api/item/spu/{spuId}}），口径收敛后不再有「卡内技术键」
     * 的例外（ADR-062 §后果 E3 已闭合）。
     */
    private RecommendSpuVO toCard(Spu spu, Integer minPrice) {
        RecommendSpuVO vo = new RecommendSpuVO();
        vo.setSpuId(spu.getSpuId());
        vo.setSpuName(spu.getName());
        vo.setMainImage(spu.getMainImage());
        vo.setPrice(minPrice);
        vo.setSoldCount(spu.getSoldCount());
        return vo;
    }

    /** size 归一（P35 契约：缺省 6、≤10；非正按缺省处理）。 */
    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_CARD_SIZE;
        }
        return Math.min(size, MAX_CARD_SIZE);
    }

    private boolean isOnShelf(Spu spu) {
        return spu.getStatus() != null
                && spu.getStatus() == SpuStatusEnum.ON_SHELF.getCode();
    }

    /**
     * 搭配候选（类目内销量序取数 + 全局权重/销量重排的中间形态）。
     *
     * @param spu 在售商品聚合
     * @param weight  所经 PAIR_WITH 规则权重
     */
    private record Candidate(Spu spu, int weight) {
    }
}
