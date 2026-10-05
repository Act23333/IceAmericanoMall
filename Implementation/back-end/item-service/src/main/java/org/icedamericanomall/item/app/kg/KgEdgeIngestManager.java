package org.icedamericanomall.item.app.kg;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.KgClient;
import org.icedamericanomall.dto.KgEdgeItemCO;
import org.icedamericanomall.dto.KgEdgeUpsertCO;
import org.icedamericanomall.fallback.KgClientFallback;
import org.icedamericanomall.item.domain.enums.ProductStatusEnum;
import org.icedamericanomall.item.domain.model.kg.BrandKeys;
import org.icedamericanomall.item.domain.model.kg.KgSchema;
import org.icedamericanomall.item.domain.model.kg.PairingRule;
import org.icedamericanomall.item.domain.model.product.Product;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识图谱边摄取协作器（F15 Phase 1）—— item-service 侧 KG 边投影写入的<b>唯一收口</b>
 * （镜像 {@code ProductRecommendManager} 向量摄取先例，ADR-046/047/048 同款纪律）。
 *
 * <p><b>事务纪律（硬约束：Feign 绝不入事务）</b>：摄取发生在真相落库成功<b>之后</b>——
 * <ul>
 *   <li>调用方处于活动事务 → 注册 {@code afterCommit} 回调，事务提交后才发送：
 *       回滚即不摄取；写入载荷在注册时由聚合/规则快照构建，字段值提交前冻结；</li>
 *   <li>无活动事务（如商品编辑单语句路径）→ 直接发送。</li>
 * </ul>
 *
 * <p><b>fail-open（PRD §16.8.1 最高优先级纪律）</b>：KG 是纯旁路增强能力——降级哨兵
 * {@code -1}（I57 对齐 I47 口径）/ 任何异常只记 WARN，商品保存与治理落库主流程零感知；
 * 漏摄取由 AD64 全量重建兜底（幂等可重试）。
 *
 * <p><b>边覆盖口径</b>：本类逐次摄取商品直建边（BELONGS_TO / BRAND_OF / SOLD_BY）与
 * 规则边（PAIR_WITH，治理写面同步投影）。PARENT_OF 类目层级边为低频真相（类目树几乎不变），
 * Phase 1 <b>不做逐次摄取</b>，由 AD64 全量重建统一覆盖。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KgEdgeIngestManager {

    private final KgClient kgClient;

    // ==================== 商品直建边（I57 摄取 / I59 清边） ====================

    /**
     * 商品直建边摄取（事务外旁路）：在售 → 按先删后写幂等覆盖 BELONGS_TO / BRAND_OF /
     * SOLD_BY 三类边；下架/删除 → 清边（I59，配合下架清理口径）。
     *
     * <p>未落库（id 为空）静默跳过，不产生任何远程调用。
     *
     * @param product 刚落库/变更后的商品聚合快照
     */
    public void ingestProductAfterCommit(Product product) {
        if (product == null || product.getId() == null) {
            log.debug("KG 商品直建边摄取跳过：商品未落库");
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            writeProductEdges(product);
            return;
        }
        if (isOnShelf(product)) {
            KgEdgeUpsertCO co = buildProductDirectEdges(product);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    upsertSafely(co);
                }
            });
        } else {
            Long productId = product.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteEdgesSafely(KgSchema.NODE_PRODUCT, productId);
                }
            });
        }
    }

    /**
     * 商品直建边同步写入（AD64 重建路径用）：在售 → 幂等覆盖写；下架/删除 → 清边。
     *
     * @param product 商品聚合
     * @return 成功写入边数（清边/降级/失败为 0，fail-open 不抛）
     */
    public int writeProductEdges(Product product) {
        if (product == null || product.getId() == null) {
            return 0;
        }
        if (!isOnShelf(product)) {
            deleteEdgesSafely(KgSchema.NODE_PRODUCT, product.getId());
            return 0;
        }
        return upsertSafely(buildProductDirectEdges(product));
    }

    // ==================== 规则边（PAIR_WITH 投影同步，治理写面） ====================

    /**
     * 指定源类目的 PAIR_WITH 边投影同步（事务外旁路）：按规则快照先删后写幂等覆盖——
     * 创建/启停即时生效的落点（停用规则以 status=0 写入投影，I58 缺省按启用过滤）。
     *
     * @param srcCategoryId 源类目 ID
     * @param rules         该源类目下的全部规则快照（含停用）
     */
    public void syncPairingEdgesAfterCommit(Long srcCategoryId, List<PairingRule> rules) {
        if (srcCategoryId == null) {
            return;
        }
        List<PairingRule> snapshot = rules == null ? List.of() : List.copyOf(rules);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            writePairingEdges(srcCategoryId, snapshot);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                writePairingEdges(srcCategoryId, snapshot);
            }
        });
    }

    /**
     * PAIR_WITH 边同步写入（AD64 重建路径用）。
     *
     * @param srcCategoryId 源类目 ID
     * @param rules         规则快照（含停用）
     * @return 成功写入边数（降级/失败为 0，fail-open 不抛）
     */
    public int writePairingEdges(Long srcCategoryId, List<PairingRule> rules) {
        if (srcCategoryId == null || rules == null || rules.isEmpty()) {
            return 0;
        }
        return upsertSafely(buildPairingEdgesUpsert(srcCategoryId, rules));
    }

    // ==================== 通用写入（重建编排复用） ====================

    /**
     * 边批量摄取（fail-open）：降级哨兵 {@code -1} / 任何异常只记 WARN 并按 0 边计，
     * 绝不向调用方抛出（重建逐 src 继续，对齐 ProductRecommendManager 防御口径）。
     *
     * @param upsert 摄取载荷
     * @return 成功写入边数
     */
    public int upsertSafely(KgEdgeUpsertCO upsert) {
        if (upsert == null || upsert.getEdges() == null || upsert.getEdges().isEmpty()) {
            return 0;
        }
        try {
            int written = kgClient.edgeUpsert(upsert);
            if (written == KgClientFallback.UPSERT_FALLBACK_SENTINEL) {
                log.warn("KG 边摄取降级（哨兵-1），本批边未投影可由 AD64 重建补齐: srcType={}, srcId={}, edgeCount={}",
                        upsert.getSrcType(), upsert.getSrcId(), upsert.getEdges().size());
                return 0;
            }
            log.debug("KG 边摄取完成: srcType={}, srcId={}, written={}",
                    upsert.getSrcType(), upsert.getSrcId(), written);
            return Math.max(written, 0);
        } catch (Exception e) {
            log.warn("KG 边摄取失败，主流程不受影响: srcType={}, srcId={}, edgeCount={}",
                    upsert.getSrcType(), upsert.getSrcId(),
                    upsert.getEdges() == null ? 0 : upsert.getEdges().size(), e);
            return 0;
        }
    }

    /**
     * 按 src 清边（fail-open，I59）：下架/删除商品与重建清理共用；降级为空操作仅记 WARN。
     *
     * @param srcType 源节点类型
     * @param srcId   源节点 id
     */
    public void deleteEdgesSafely(String srcType, Long srcId) {
        try {
            kgClient.deleteEdges(srcType, srcId);
            log.debug("KG 清边完成: srcType={}, srcId={}", srcType, srcId);
        } catch (Exception e) {
            log.warn("KG 清边失败，主流程不受影响（AD64 重建兜底）: srcType={}, srcId={}", srcType, srcId, e);
        }
    }

    // ==================== 载荷构建 ====================

    /**
     * 商品聚合 → 直建边摄取载荷（BELONGS_TO / BRAND_OF / SOLD_BY，source=DIRECT）。
     *
     * <p>brand 为规范化字符串值节点（O4）：brandKey 规则与 CRC-32 节点 id 口径见
     * {@link BrandKeys}；brand/category/seller 缺失的字段跳过对应边，不产悬空端点。
     */
    private KgEdgeUpsertCO buildProductDirectEdges(Product product) {
        List<KgEdgeItemCO> edges = new ArrayList<>(3);
        if (product.getCategoryId() != null) {
            edges.add(new KgEdgeItemCO(KgSchema.REL_BELONGS_TO, KgSchema.NODE_CATEGORY,
                    product.getCategoryId(), 0, KgSchema.SOURCE_DIRECT, KgSchema.EDGE_ENABLED));
        }
        if (BrandKeys.brandKey(product.getBrand()) != null) {
            edges.add(new KgEdgeItemCO(KgSchema.REL_BRAND_OF, KgSchema.NODE_BRAND,
                    BrandKeys.nodeId(product.getBrand()), 0, KgSchema.SOURCE_DIRECT, KgSchema.EDGE_ENABLED));
        }
        if (product.getSellerId() != null) {
            edges.add(new KgEdgeItemCO(KgSchema.REL_SOLD_BY, KgSchema.NODE_SELLER,
                    product.getSellerId(), 0, KgSchema.SOURCE_DIRECT, KgSchema.EDGE_ENABLED));
        }
        return new KgEdgeUpsertCO(KgSchema.NODE_PRODUCT, product.getId(), edges);
    }

    /**
     * 规则快照 → PAIR_WITH 边摄取载荷（source=RULE；weight/status 原样透传——
     * 停用规则以 status=0 入投影，启停即时生效由查询侧 status 过滤实现）。
     */
    private KgEdgeUpsertCO buildPairingEdgesUpsert(Long srcCategoryId, List<PairingRule> rules) {
        List<KgEdgeItemCO> edges = rules.stream()
                .map(rule -> new KgEdgeItemCO(KgSchema.REL_PAIR_WITH, KgSchema.NODE_CATEGORY,
                        rule.getDstCategoryId(), rule.getWeight() == null ? 0 : rule.getWeight(),
                        KgSchema.SOURCE_RULE,
                        rule.isEnabled() ? KgSchema.EDGE_ENABLED : KgSchema.EDGE_DISABLED))
                .toList();
        return new KgEdgeUpsertCO(KgSchema.NODE_CATEGORY, srcCategoryId, edges);
    }

    /** 商品是否在售（在售才持有直建边；下架/删除清边，与 P35 组装过滤口径一致）。 */
    private boolean isOnShelf(Product product) {
        return product.getStatus() != null
                && product.getStatus() == ProductStatusEnum.ON_SHELF.getCode();
    }
}
