package org.icedamericanomall.search.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.dto.KgEdgeUpsertCO;
import org.icedamericanomall.dto.KgNeighborCO;
import org.icedamericanomall.search.domain.gateway.KgEdgeIndexGateway;
import org.icedamericanomall.search.domain.model.kg.KgEdgeDoc;
import org.icedamericanomall.search.domain.model.kg.KgNeighborHit;
import org.icedamericanomall.search.domain.model.kg.KgNodeRef;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 图边编排（应用层）—— 摄取 / 邻居检索 / 清边（{@code kg_edges} 索引归 search-service 所有）。
 *
 * <p><b>摄取链路（I57）</b>：按 src 先删后写幂等（对齐 I37/I47 模式）——
 * ensureIndex → deleteBySrc → indexEdges；空边集合等价「清空该 src 全部出边」。
 *
 * <p><b>邻居链路（I58）</b>：terms 往返 ≤2 次 —— depth=1 一次查询出直接邻居；
 * depth=2 以第一跳目标节点集合为起点发起第二次 terms 查询。能力边界 ≤2 跳
 * （PRD §16.4 明示），depth &gt; 2 拒绝（400 PARAM_ERROR）。
 *
 * <p><b>渐进启用 / 全链路降级（KG 纯旁路，PRD §16.8.1）</b>：ES 关闭时本 Bean 不存在，
 * Controller 按「未启用」处理（I57 返回 0、I58 空列表、I59 空操作），不阻断主流程；
 * 写路径失败 fail-loud 由调用方（item-service）捕获降级，读路径由网关实现 fail-open。
 *
 * <p>仅 {@code search.elasticsearch.enabled=true} 时装配（依赖 ES 网关实现）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "true")
public class KgEdgeManager {

    /** 图查询能力边界：最多 2 跳（PRD §16.4 明示，≥3 跳不做）。 */
    public static final int MAX_DEPTH = 2;

    /** 缺省遍历深度。 */
    private static final int DEFAULT_DEPTH = 1;

    /** 边状态：1=启用（status 入参缺省口径）。 */
    private static final int STATUS_ENABLED = 1;

    /** 直建边缺省权重。 */
    private static final int DEFAULT_WEIGHT = 0;

    /** 单次 terms 往返取回上限（I58 无 size 入参，P95 < 200ms 预算内的截断保护）。 */
    private static final int HOP_FETCH_LIMIT = 100;

    private final KgEdgeIndexGateway indexGateway;

    /**
     * 按 src 先删后写批量摄取边（幂等：重复摄取覆盖旧边，不产生漂移 doc）。
     *
     * @param upsert 源节点 + 边集合（空边集合 = 清空该 src 全部出边）
     * @return 写入边数
     * @throws BadRequestException srcType/srcId/edges 缺失
     * @throws org.icedamericanomall.common.exception.BizException ES 写入失败（调用方降级为哨兵 -1）
     */
    public int upsertEdges(KgEdgeUpsertCO upsert) {
        validateUpsert(upsert);
        List<KgEdgeDoc> docs = toDocs(upsert);
        indexGateway.ensureIndex();
        indexGateway.deleteBySrc(upsert.getSrcType(), upsert.getSrcId());
        if (!docs.isEmpty()) {
            indexGateway.indexEdges(docs);
        }
        log.info("图边摄取完成: srcType={}, srcId={}, 边数={}",
                upsert.getSrcType(), upsert.getSrcId(), docs.size());
        return docs.size();
    }

    /**
     * 图邻居查询（terms 往返 ≤2 次）。
     *
     * @param srcType 源节点类型（必填）
     * @param srcId   源节点 id（必填）
     * @param relType 关系类型过滤（null = 不过滤）
     * @param dstType 目标类型过滤（null = 不过滤）
     * @param depth   遍历深度（缺省 1，≤2；&gt;2 拒绝）
     * @param status  状态过滤（null = 仅启用边）
     * @return 邻居列表（无命中为空列表，按 score/weight 稳定序去重）
     * @throws BadRequestException srcType/srcId 缺失，或 depth 超出能力边界
     */
    public List<KgNeighborCO> neighbors(String srcType, Long srcId, String relType,
                                        String dstType, Integer depth, Integer status) {
        validateSrc(srcType, srcId);
        int effectiveDepth = normalizeDepth(depth);
        int effectiveStatus = status == null ? STATUS_ENABLED : status;
        List<KgNeighborHit> firstHop = dedupByDst(indexGateway.searchNeighbors(
                List.of(new KgNodeRef(srcType, srcId)), relType, dstType, effectiveStatus, HOP_FETCH_LIMIT));
        if (effectiveDepth < MAX_DEPTH || firstHop.isEmpty()) {
            return toNeighborCOs(firstHop);
        }
        List<KgNodeRef> frontier = firstHop.stream()
                .map(hit -> new KgNodeRef(hit.dstType(), hit.dstId()))
                .toList();
        List<KgNeighborHit> secondHop = indexGateway.searchNeighbors(
                frontier, relType, dstType, effectiveStatus, HOP_FETCH_LIMIT);
        return toNeighborCOs(dedupByDst(secondHop));
    }

    /**
     * 按 src 清边（下架清理 / 重建前置）。
     *
     * @param srcType 源节点类型
     * @param srcId   源节点 id
     * @throws BadRequestException srcType/srcId 缺失
     * @throws org.icedamericanomall.common.exception.BizException ES 删除失败（调用方降级为空操作）
     */
    public void deleteEdges(String srcType, Long srcId) {
        validateSrc(srcType, srcId);
        indexGateway.ensureIndex();
        indexGateway.deleteBySrc(srcType, srcId);
    }

    private static void validateUpsert(KgEdgeUpsertCO upsert) {
        validateSrc(upsert.getSrcType(), upsert.getSrcId());
        if (upsert.getEdges() == null) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR, "edges 不能为空");
        }
    }

    private static void validateSrc(String srcType, Long srcId) {
        if (srcType == null || srcType.isBlank()) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR, "srcType 不能为空");
        }
        if (srcId == null) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR, "srcId 不能为空");
        }
    }

    private static int normalizeDepth(Integer depth) {
        if (depth == null || depth < DEFAULT_DEPTH) {
            return DEFAULT_DEPTH;
        }
        if (depth > MAX_DEPTH) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR,
                    "depth 超出图查询能力边界(≤" + MAX_DEPTH + "跳): " + depth);
        }
        return depth;
    }

    /** 契约 CO → 领域边文档（weight 缺省 0、status 缺省 1 启用）。 */
    private static List<KgEdgeDoc> toDocs(KgEdgeUpsertCO upsert) {
        return upsert.getEdges().stream()
                .map(item -> new KgEdgeDoc(upsert.getSrcType(), upsert.getSrcId(),
                        item.getRelType(), item.getDstType(), item.getDstId(),
                        item.getWeight() == null ? DEFAULT_WEIGHT : item.getWeight(),
                        item.getSource(),
                        item.getStatus() == null ? STATUS_ENABLED : item.getStatus()))
                .toList();
    }

    /** 按 dst 去重（ES 已按 score/weight 排序，保留首现即最优；两跳多路径收敛为一条邻居）。 */
    private static List<KgNeighborHit> dedupByDst(List<KgNeighborHit> hits) {
        Map<String, KgNeighborHit> unique = new LinkedHashMap<>();
        for (KgNeighborHit hit : hits) {
            unique.putIfAbsent(hit.dstType() + ":" + hit.dstId(), hit);
        }
        return List.copyOf(unique.values());
    }

    private static List<KgNeighborCO> toNeighborCOs(List<KgNeighborHit> hits) {
        return hits.stream()
                .map(hit -> new KgNeighborCO(hit.dstType(), hit.dstId(),
                        hit.relType(), hit.weight(), hit.score()))
                .toList();
    }
}
