package org.icedamericanomall.search.domain.gateway;

import org.icedamericanomall.search.domain.model.search.ProductSearchHit;
import org.icedamericanomall.search.domain.model.search.SearchPage;
import org.icedamericanomall.search.domain.model.search.SearchQuery;

import java.util.List;
import java.util.Map;

/**
 * 商品检索网关（领域端口）—— DB LIKE 与 ES 两条实现链的唯一契约。
 *
 * <p>实现方：{@code infrastructure.persistence.DbProductSearchGateway}（LIKE 降级，缺省）
 * 与 {@code infrastructure.es.EsProductSearchGateway}（{@code search.elasticsearch.enabled=true}），
 * 两者条件互补、同时最多装配一个。
 */
public interface ProductSearchGateway {

    /** 关键词/分类检索。 */
    SearchPage<ProductSearchHit> search(SearchQuery query);

    /** 向量语义检索（DB 实现恒返回空列表）。 */
    List<ProductSearchHit> vectorSearch(double[] embedding, int size);

    /** ES 全量重建索引（DB 实现返回未启用标记）。 */
    Map<String, Object> reindex();
}
