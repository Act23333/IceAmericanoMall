package org.icedamericanomall.search.domain.gateway;

import org.icedamericanomall.search.domain.model.search.SpuSearchHit;
import org.icedamericanomall.search.domain.model.search.SpuVectorDoc;
import org.icedamericanomall.search.domain.model.search.SearchFacets;
import org.icedamericanomall.search.domain.model.search.SearchPage;
import org.icedamericanomall.search.domain.model.search.SearchQuery;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 商品检索网关（领域端口）—— DB LIKE 与 ES 两条实现链的唯一契约。
 *
 * <p>实现方：{@code infrastructure.adapter.ItemServiceSpuSearchGateway}（item 域检索端点降级，缺省；ADR-052）
 * 与 {@code infrastructure.es.EsSpuSearchGateway}（{@code search.elasticsearch.enabled=true}），
 * 两者条件互补、同时最多装配一个。
 */
public interface SpuSearchGateway {

    /** 关键词/分类检索。 */
    SearchPage<SpuSearchHit> search(SearchQuery query);

    /**
     * 搜索 facet 聚合（SE6）—— 品牌 / 固定价格档位计数。
     *
     * <p>只按 {@code keyword}/{@code categoryId} 过滤（不含 brand/price 过滤——facet 反映过滤前分布）；
     * DB LIKE 实现无聚合能力（facet 是 ES 能力），恒返回 {@link SearchFacets#empty()}。
     *
     * @param query 检索条件（仅 keyword/categoryId 生效）
     * @return facet 计数；无聚合能力或聚合异常时为空对象（辅助 UI 降级，不阻断）
     */
    SearchFacets facets(SearchQuery query);

    /** 向量语义检索（DB 实现恒返回空列表）。 */
    List<SpuSearchHit> vectorSearch(double[] embedding, int size);

    /** ES 全量重建索引（DB 实现返回未启用标记）。 */
    Map<String, Object> reindex();

    /**
     * 商品向量文档幂等覆盖写（契约 I47）—— 按 spuId 幂等，整文档以入参为准
     * （含向量，以新向量为准；ADR-046）。DB 实现为空操作。
     *
     * @param doc 商品向量文档（展示字段 + 向量）
     */
    void upsertVectorDoc(SpuVectorDoc doc);

    /**
     * 取商品自身向量（供 I48 以自身向量做 kNN 检索）。
     * DB 实现恒返回 empty；ES 实现在商品未摄取/读取失败时也返回 empty（fail-open）。
     *
     * @param spuId 商品 id（幂等键，与 {@link #upsertVectorDoc(SpuVectorDoc)} 同源）
     * @return 商品向量；不存在时 empty
     */
    Optional<double[]> findVectorById(Long spuId);
}
