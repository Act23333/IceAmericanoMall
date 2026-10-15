package org.icedamericanomall.search.adapter.internal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.dto.KgEdgeItemCO;
import org.icedamericanomall.dto.KgEdgeUpsertCO;
import org.icedamericanomall.dto.KgNeighborCO;
import org.icedamericanomall.search.adapter.request.KgEdgeUpsertReq;
import org.icedamericanomall.search.app.KgEdgeManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 内部知识图谱接口 — 图边摄取（I57）/ 邻居查询（I58）/ 清边（I59），契约 §4.14.10b。
 *
 * <p>调用方 item-service（ia-api {@code KgClient}），全链路 fail-open（KG 纯旁路增强能力，
 * PRD §16.8.1）：本服务 ES 关闭时按「未启用」处理 —— I57 返回 0、I58 返回空列表、
 * I59 空操作，<b>不抛异常</b>，商品保存与详情主流程零感知（与 I47/I50 口径一致）。
 *
 * <p>内部接口（{@code /internal/**}）按约定不包装 {@code Result}，异常原样透出（ADR-004）。
 * Controller 仅做参数解析 + 委托 {@link KgEdgeManager}。
 */
@Slf4j
@RestController
@RequestMapping("/internal/search/kg")
@RequiredArgsConstructor
public class InternalKgController {

    /** ES 关闭时无此 Bean，故用 ObjectProvider 软依赖（不阻断启动，按未启用处理）。 */
    private final ObjectProvider<KgEdgeManager> kgEdgeManager;

    /**
     * 图边批量摄取（I57）—— 按 src 先删后写幂等。
     *
     * @return 写入边数；未启用返回 0（Feign 降级哨兵 -1 由 ia-api fallback 承担）
     */
    @PostMapping("/edge/upsert")
    public int upsertEdges(@RequestBody @Valid KgEdgeUpsertReq req) {
        KgEdgeManager manager = kgEdgeManager.getIfAvailable();
        if (manager == null) {
            log.warn("图边摄取未启用（ES 关闭），跳过: srcType={}, srcId={}", req.srcType(), req.srcId());
            return 0;
        }
        return manager.upsertEdges(toCO(req));
    }

    /**
     * 图邻居查询（I58）—— terms 往返 ≤2 跳（depth &gt; 2 由 Manager 拒绝为 400）。
     *
     * @return 邻居列表；未启用/无命中返回空列表
     */
    @GetMapping("/neighbors")
    public List<KgNeighborCO> neighbors(@RequestParam(name = "src_type") String srcType,
                                        @RequestParam(name = "src_id") Long srcId,
                                        @RequestParam(name = "rel_type", required = false) String relType,
                                        @RequestParam(name = "dst_type", required = false) String dstType,
                                        @RequestParam(value = "depth", defaultValue = "1") Integer depth,
                                        @RequestParam(value = "status", required = false) Integer status) {
        KgEdgeManager manager = kgEdgeManager.getIfAvailable();
        if (manager == null) {
            log.warn("图邻居查询未启用（ES 关闭），按无命中处理: srcType={}, srcId={}", srcType, srcId);
            return List.of();
        }
        return manager.neighbors(srcType, srcId, relType, dstType, depth, status);
    }

    /**
     * 按 src 清边（I59，下架清理 / 重建前置）。
     */
    @DeleteMapping("/edges")
    public void deleteEdges(@RequestParam(name = "src_type") String srcType,
                            @RequestParam(name = "src_id") Long srcId) {
        KgEdgeManager manager = kgEdgeManager.getIfAvailable();
        if (manager == null) {
            log.warn("图边清理未启用（ES 关闭），跳过: srcType={}, srcId={}", srcType, srcId);
            return;
        }
        manager.deleteEdges(srcType, srcId);
    }

    /** 适配层入参 → 契约 CO（字段一一对应，纯搬运）。 */
    private static KgEdgeUpsertCO toCO(KgEdgeUpsertReq req) {
        List<KgEdgeItemCO> items = req.edges().stream()
                .map(item -> new KgEdgeItemCO(
                        item.relType(), item.dstType(), item.dstId(),
                        item.weight(), item.source(), item.status()))
                .toList();
        return new KgEdgeUpsertCO(req.srcType(), req.srcId(), items);
    }
}
