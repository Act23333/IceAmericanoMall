package org.icedamericanomall.client;

import org.icedamericanomall.dto.KgEdgeUpsertCO;
import org.icedamericanomall.dto.KgNeighborCO;
import org.icedamericanomall.fallback.KgClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 知识图谱检索面 Feign 客户端（F15 KG：item-service → search-service，契约 §4.14.10b I57-I59，V5.16）。
 *
 * <p>调用方：item-service（直建边 afterCommit 摄取 / 治理重建 / P35 搭配卡消费）。
 *
 * <p><b>降级语义：全链路 fail-open（PRD §16.8.1 最高优先级纪律）</b>——KG 是纯旁路增强能力，
 * 与 ADR-046「推荐纯旁路全链路 fail-open，与 I43/I44 fail-closed 刻意不同」同款定类：
 * <ul>
 *   <li>I57 摄取降级返回哨兵 {@code -1}（对齐 I47 口径），调用方 WARN 后继续主流程；</li>
 *   <li>I58 邻居查询降级返回空列表（展示型查询返回空值，ADR-011 三分法）；</li>
 *   <li>I59 清边降级为空操作。</li>
 * </ul>
 * KG 失效绝不阻断商品保存与详情主流程。
 */
@FeignClient(name = "search-service", path = "/internal/search/kg",
        contextId = "kg", fallbackFactory = KgClientFallback.class)
public interface KgClient {

    /** 按 src 先删后写批量摄取边；返回写入边数（未启用 0 / 降级 -1）。 */
    @PostMapping("/edge/upsert")
    int edgeUpsert(@RequestBody KgEdgeUpsertCO upsert);

    /** 图邻居查询（≤2 跳；降级返回空列表）。 */
    @GetMapping("/neighbors")
    List<KgNeighborCO> neighbors(@RequestParam(name = "src_type") String srcType,
                                 @RequestParam(name = "src_id") Long srcId,
                                 @RequestParam(name = "rel_type", required = false) String relType,
                                 @RequestParam(name = "dst_type", required = false) String dstType,
                                 @RequestParam(value = "depth", defaultValue = "1") Integer depth,
                                 @RequestParam(value = "status", required = false) Integer status);

    /** 按 src 清边（下架清理 / 重建前置）。 */
    @DeleteMapping("/edges")
    void deleteEdges(@RequestParam(name = "src_type") String srcType, @RequestParam(name = "src_id") Long srcId);
}
