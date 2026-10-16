package org.icedamericanomall.client;

import org.icedamericanomall.dto.SpuVectorHitCO;
import org.icedamericanomall.dto.SpuVectorUpsertCO;
import org.icedamericanomall.fallback.SpuVectorClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 商品向量推荐 Feign 客户端（F7 商品向量推荐：item-service → search-service，
 * 契约 §4.14.10 I47-I48，V5.9）。
 *
 * <p>调用方：item-service——商品创建/编辑后摄取向量（事务外旁路）、商品详情页相似推荐组装。
 *
 * <p><b>降级语义：fail-open（纯旁路，ADR-046）</b>——推荐是体验增强而非交易必需：
 * <ul>
 *   <li>摄取（I47）失败返回哨兵 {@code -1}（对齐知识库 KnowledgeVectorClient 哨兵口径），
 *       商品主流程零感知；ES/Embedding 未启用时服务端正常返回 {@code 0}（静默跳过）；</li>
 *   <li>相似检索（I48）失败返回空列表——推荐位可空是接受的产品形态，商品页不可被推荐拖垮
 *       （与 I43/I44 fail-closed 刻意不同：定价是资金事实，推荐不是）。</li>
 * </ul>
 *
 * <p><b>路径口径（ADR-062 §决策 2）</b>：SPU 资源段为 {@code spu} —— 类级前缀
 * {@code /internal/search/vector/spu} 与 §4.14.10 I47/I48 同步（原 {@code .../product}）。
 */
@FeignClient(name = "search-service", path = "/internal/search/vector/spu",
        contextId = "spuVector", fallbackFactory = SpuVectorClientFallback.class)
public interface SpuVectorClient {

    /**
     * 商品向量幂等覆盖写（单向量，name+description 文本源，ADR-046）。
     *
     * @param co 商品原文与展示字段（spuId 幂等键）
     * @return 1=已写入向量索引；0=ES/Embedding 未启用（静默跳过）；降级哨兵=-1
     */
    @PostMapping("/upsert")
    int upsert(@RequestBody SpuVectorUpsertCO co);

    /**
     * 相似商品检索（自身向量 kNN，排除自身）。
     *
     * <p><b>入参口径（ADR-061 §决策 2）</b>：{@code spuId} 为 SPU <b>业务键</b>（32-hex），
     * 与 {@link SpuVectorUpsertCO#getSpuId()}、{@link SpuVectorHitCO#getSpuId()} 同源；
     * 技术键解析在 search-service 文档落库边界完成。
     *
     * @param spuId SPU 业务键（32-hex）
     * @param size  返回条数（缺省 5，≤20）
     * @return 命中 {@code spuId（业务键）}+ 相似度得分（按相似度降序）；降级/无向量时为空列表
     */
    @GetMapping("/similar")
    List<SpuVectorHitCO> similar(@RequestParam(name = "spu_id") String spuId,
                                     @RequestParam(value = "size", defaultValue = "5") int size);
}
