package org.icedamericanomall.item.app.kg;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.KgClient;
import org.icedamericanomall.dto.KgEdgeItemCO;
import org.icedamericanomall.dto.KgEdgeUpsertCO;
import org.icedamericanomall.fallback.KgClientFallback;
import org.icedamericanomall.item.domain.enums.SpuStatusEnum;
import org.icedamericanomall.item.domain.model.kg.BrandKeys;
import org.icedamericanomall.item.domain.model.kg.KgSchema;
import org.icedamericanomall.item.domain.model.kg.PairingRule;
import org.icedamericanomall.item.domain.model.spu.Spu;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识图谱边摄取协作器（F15 Phase 1）—— item-service 侧 KG 边投影写入的<b>唯一收口</b>
 * （镜像 {@code SpuRecommendManager} 向量摄取先例，ADR-046/047/048 同款纪律）。
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
     * @param spu 刚落库/变更后的商品聚合快照
     */
    public void ingestSpuAfterCommit(Spu spu) {
        if (spu == null || spu.getId() == null) {
            log.debug("KG 商品直建边摄取跳过：商品未落库");
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            writeSpuEdges(spu);
            return;
        }
        if (isOnShelf(spu)) {
            KgEdgeUpsertCO co = buildSpuDirectEdges(spu);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    upsertSafely(co);
                }
            });
        } else {
            Long spuId = spu.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteEdgesSafely(KgSchema.NODE_PRODUCT, spuId);
                }
            });
        }
    }

    /**
     * 商品直建边同步写入（AD64 重建路径用）：在售 → 幂等覆盖写；下架/删除 → 清边。
     *
     * @param spu 商品聚合
     * @return 成功写入边数（清边/降级/失败为 0，fail-open 不抛）
     */
    public int writeSpuEdges(Spu spu) {
        if (spu == null || spu.getId() == null) {
            return 0;
        }
        if (!isOnShelf(spu)) {
            deleteEdgesSafely(KgSchema.NODE_PRODUCT, spu.getId());
            return 0;
        }
        return upsertSafely(buildSpuDirectEdges(spu));
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
     * 绝不向调用方抛出（重建逐 src 继续，对齐 SpuRecommendManager 防御口径）。
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
     * 按 src 清边（I59）：下架/删除商品与重建清理共用；<b>失败仍 fail-open</b>（降级只记日志，不阻断主流程）。
     *
     * <p><b>⚠ 收敛路径的诚实口径（U17 catch 体审计，2026-10-18；此前写「AD64 重建兜底」是错的）</b>：
     * 管理端重建（{@code KgAdminManager#rebuild}）**不是全量重建、也不做全局清空** —— 它只
     * {@code spuRepository.pageAll(...)} 扫描<b>仍存在</b>的 SPU，再按在售状态 upsert 或清边。
     * 因此收敛能力只覆盖「仍存在但已下架」这一种情形：<b>SPU 行已删除时，重建根本扫不到它，
     * 其残留边没有任何自动消费者清理</b>。故本方法的准确结论是
     * **「暂无可靠消费者」**（仅部分场景有管理端人工入口），已登记在
     * {@code collaboration/open-items.md} 的 U17 —— <b>不要</b>再写「重建兜底」。
     *
     * @param srcType 源节点类型
     * @param srcId   源节点 id
     */
    public void deleteEdgesSafely(String srcType, Long srcId) {
        try {
            kgClient.deleteEdges(srcType, srcId);
            log.debug("KG 清边完成: srcType={}, srcId={}", srcType, srcId);
        } catch (Exception e) {
            // 可定位键：srcType + srcId；无自动重试/可靠对账消费者（见上方口径）
            log.warn("KG 清边失败，主流程不受影响（⚠ 无可靠消费者：重建只覆盖「仍存在且已下架」的 SPU，"
                    + "已删除 SPU 的残留边需人工处理）: srcType={}, srcId={}", srcType, srcId, e);
        }
    }

    // ==================== 载荷构建 ====================

    /**
     * 商品聚合 → 直建边摄取载荷（BELONGS_TO / BRAND_OF / SOLD_BY，source=DIRECT）。
     *
     * <p>brand 为规范化字符串值节点（O4）：brandKey 规则与 CRC-32 节点 id 口径见
     * {@link BrandKeys}；brand/category/seller 缺失的字段跳过对应边，不产悬空端点。
     */
    private KgEdgeUpsertCO buildSpuDirectEdges(Spu spu) {
        List<KgEdgeItemCO> edges = new ArrayList<>(3);
        if (spu.getCategoryId() != null) {
            edges.add(new KgEdgeItemCO(KgSchema.REL_BELONGS_TO, KgSchema.NODE_CATEGORY,
                    spu.getCategoryId(), 0, KgSchema.SOURCE_DIRECT, KgSchema.EDGE_ENABLED));
        }
        if (BrandKeys.brandKey(spu.getBrand()) != null) {
            edges.add(new KgEdgeItemCO(KgSchema.REL_BRAND_OF, KgSchema.NODE_BRAND,
                    BrandKeys.nodeId(spu.getBrand()), 0, KgSchema.SOURCE_DIRECT, KgSchema.EDGE_ENABLED));
        }
        if (spu.getSellerId() != null) {
            edges.add(new KgEdgeItemCO(KgSchema.REL_SOLD_BY, KgSchema.NODE_SELLER,
                    spu.getSellerId(), 0, KgSchema.SOURCE_DIRECT, KgSchema.EDGE_ENABLED));
        }
        return new KgEdgeUpsertCO(KgSchema.NODE_PRODUCT, spu.getId(), edges);
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
    private boolean isOnShelf(Spu spu) {
        return spu.getStatus() != null
                && spu.getStatus() == SpuStatusEnum.ON_SHELF.getCode();
    }
}
