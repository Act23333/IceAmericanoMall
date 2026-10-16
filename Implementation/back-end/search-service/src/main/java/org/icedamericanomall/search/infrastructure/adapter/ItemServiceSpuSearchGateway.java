package org.icedamericanomall.search.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.ItemSearchClient;
import org.icedamericanomall.dto.SpuSearchItemCO;
import org.icedamericanomall.dto.SpuSearchPageCO;
import org.icedamericanomall.dto.SpuSearchQueryCO;
import org.icedamericanomall.search.domain.gateway.SpuSearchGateway;
import org.icedamericanomall.search.domain.model.search.SpuSearchHit;
import org.icedamericanomall.search.domain.model.search.SpuVectorDoc;
import org.icedamericanomall.search.domain.model.search.SearchFacets;
import org.icedamericanomall.search.domain.model.search.SearchPage;
import org.icedamericanomall.search.domain.model.search.SearchQuery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * item-service 检索网关 —— ES 未启用时的缺省实现（ADR-052：直读退出后的数据面）。
 *
 * <p>原 {@code DbSpuSearchGateway} 直读 {@code spu}/{@code sku} 镜像表的实现已退出：
 * 商品数据面归 item-service 所有，LIKE 降级检索经 {@link ItemSearchClient}
 * （item 内部检索端点，SQL 语义逐条对齐原直读实现）在线获取。
 *
 * <p>SE1 过滤与 ES 链同口径（由 item 侧 SQL 承载：brand eq / 价格区间按在售 SKU 最低价 /
 * 排序 sales|price|newest|relevance）；向量检索与向量摄取均不支持（向量是 ES 能力）：
 * {@code vectorSearch} 恒空、{@code upsertVectorDoc} 空操作、{@code findVectorById} 恒 empty；
 * reindex 不适用（返回未启用标记）；facet 聚合同为 ES 能力（SE6），恒返回空对象。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "false", matchIfMissing = true)
public class ItemServiceSpuSearchGateway implements SpuSearchGateway {

    private final ItemSearchClient itemSearchClient;

    @Override
    public SearchPage<SpuSearchHit> search(SearchQuery query) {
        SpuSearchPageCO page = itemSearchClient.searchPage(toQueryCO(query));
        if (page == null || page.getRecords() == null) {
            return SearchPage.empty(query.page(), query.size());
        }
        List<SpuSearchHit> hits = page.getRecords().stream()
                .map(ItemServiceSpuSearchGateway::toHit)
                .toList();
        return new SearchPage<>(hits, page.getTotal(), query.page(), query.size());
    }

    /**
     * facet 聚合（SE6）—— item 检索端点无聚合能力（facet 是 ES 能力），恒返回空对象，
     * C 端过滤 UI 按无 facet 降级展示，不报错。
     */
    @Override
    public SearchFacets facets(SearchQuery query) {
        return SearchFacets.empty();
    }

    @Override
    public List<SpuSearchHit> vectorSearch(double[] embedding, int size) {
        // item 检索端点不支持向量搜索，返回空列表（降级处理）
        return Collections.emptyList();
    }

    @Override
    public Map<String, Object> reindex() {
        return Map.of("status", "ok", "count", 0, "esEnabled", false);
    }

    @Override
    public void upsertVectorDoc(SpuVectorDoc doc) {
        // 非 ES 链无向量能力，摄取为空操作（向量是 ES 能力，仅 ES 链路实现）
    }

    @Override
    public Optional<double[]> findVectorById(Long spuId) {
        // 非 ES 链无向量能力，恒视为无向量（fail-open）
        return Optional.empty();
    }

    private static SpuSearchQueryCO toQueryCO(SearchQuery query) {
        SpuSearchQueryCO co = new SpuSearchQueryCO();
        co.setKeyword(query.keyword());
        co.setCategoryId(query.categoryId());
        co.setBrand(query.brand());
        co.setMinPrice(query.minPrice());
        co.setMaxPrice(query.maxPrice());
        co.setSort(query.sort());
        co.setPage(query.page());
        co.setSize(query.size());
        return co;
    }

    private static SpuSearchHit toHit(SpuSearchItemCO item) {
        return new SpuSearchHit(item.getId(), item.getSpuId(), item.getCategoryId(), item.getName(),
                item.getDescription(), item.getBrand(), item.getMainImage(), item.getMinPrice(), item.getSoldCount());
    }
}
