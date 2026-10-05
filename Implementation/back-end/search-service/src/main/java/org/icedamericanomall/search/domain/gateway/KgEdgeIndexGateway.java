package org.icedamericanomall.search.domain.gateway;

import org.icedamericanomall.search.domain.model.kg.KgEdgeDoc;
import org.icedamericanomall.search.domain.model.kg.KgNeighborHit;
import org.icedamericanomall.search.domain.model.kg.KgNodeRef;

import java.util.List;

/**
 * 图边索引网关（领域端口）—— {@code kg_edges} 边索引的建/删/写/邻居检索抽象。
 *
 * <p>实现方：{@code infrastructure.es.KgEdgeIndexAdapter}（低层 ElasticsearchClient）。
 * 幂等口径：写入侧由调用方先 {@link #deleteBySrc(String, Long)} 再
 * {@link #indexEdges(List)}（先删后写，对齐 I37/I47 模式）；doc _id 固定为
 * {@code srcType:srcId:relType:dstType:dstId}，重复摄取不产生漂移 doc。
 *
 * <p>失败口径（对齐 KG 纯旁路降级纪律，PRD §16.8.1）：写路径 fail-loud
 * （抛 {@code BizException}，由调用方 item-service 捕获降级为哨兵），
 * 读路径 fail-open（返回空列表，等价「无命中」）。
 */
public interface KgEdgeIndexGateway {

    /** 确保索引存在（不存在则按 keyword/数值 mapping 创建；并发已建则静默通过）。 */
    void ensureIndex();

    /** 按 src（srcType + srcId）删除该源节点的全部出边。 */
    void deleteBySrc(String srcType, Long srcId);

    /**
     * 批量写入边文档（每边一条 doc，_id = edgeId）。
     *
     * @param edges 边集合（空集合为空操作）
     */
    void indexEdges(List<KgEdgeDoc> edges);

    /**
     * 邻居检索：以给定源节点集合为一跳起点做 terms 查询（可按关系/目标类型/状态过滤）。
     *
     * @param srcNodes 源节点集合（一跳起点；两跳查询由调用方发起第二次检索）
     * @param relType  关系类型过滤（null = 不过滤）
     * @param dstType  目标类型过滤（null = 不过滤）
     * @param status   状态过滤（null = 不过滤；调用方缺省传 1=仅启用）
     * @param limit    取回上限
     * @return 邻居命中列表（无结果/检索失败为空列表，不抛异常）
     */
    List<KgNeighborHit> searchNeighbors(List<KgNodeRef> srcNodes, String relType,
                                        String dstType, Integer status, int limit);
}
