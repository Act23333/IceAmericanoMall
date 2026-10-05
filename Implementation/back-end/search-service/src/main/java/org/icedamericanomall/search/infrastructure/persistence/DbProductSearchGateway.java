package org.icedamericanomall.search.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.search.domain.gateway.ProductSearchGateway;
import org.icedamericanomall.search.domain.model.search.ProductSearchHit;
import org.icedamericanomall.search.domain.model.search.ProductVectorDoc;
import org.icedamericanomall.search.domain.model.search.SearchFacets;
import org.icedamericanomall.search.domain.model.search.SearchPage;
import org.icedamericanomall.search.domain.model.search.SearchQuery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DB LIKE 检索网关 —— ES 未启用时的缺省实现（MyBatis-Plus lambdaQuery 参数化，杜绝 SQL 注入）。
 *
 * <p>SE1 过滤与 ES 链同口径：brand eq（MySQL ci collation）/ 价格区间按在售 SKU 最低价
 * （{@code HAVING MIN(price)} 子查询）/ 排序 sales|price|newest|relevance。
 *
 * <p>向量检索与向量摄取均不支持（向量是 ES 能力）：{@code vectorSearch} 恒返回空列表、
 * {@code upsertVectorDoc} 为空操作、{@code findVectorById} 恒 empty；reindex 不适用（返回未启用标记）；
 * facet 聚合同为 ES 能力（SE6），{@code facets} 恒返回空对象。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "false", matchIfMissing = true)
public class DbProductSearchGateway implements ProductSearchGateway {

    /** 在售状态（item-service 商品域的口径，此处只读镜像表）。 */
    private static final int STATUS_ON_SALE = 1;

    /**
     * price 排序（SE1）：按在售 SKU 最低价升序的关联子查询。
     * 片段仅含常量（status 为常量值），无用户自由文本，无注入面；
     * LambdaQueryWrapper 不支持表达式排序，故用 last() 追加（分页插件在其后拼 LIMIT，不受影响）。
     */
    private static final String MIN_PRICE_ORDER_ASC =
            " ORDER BY (SELECT MIN(sku.price) FROM sku WHERE sku.product_id = product.id"
                    + " AND sku.status = " + STATUS_ON_SALE + ") ASC";

    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;

    @Override
    public SearchPage<ProductSearchHit> search(SearchQuery query) {
        LambdaQueryWrapper<ProductPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductPO::getStatus, STATUS_ON_SALE);

        if (query.hasKeyword()) {
            wrapper.and(w -> w
                    .like(ProductPO::getName, query.keyword())
                    .or()
                    .like(ProductPO::getDescription, query.keyword()));
        }
        if (query.categoryId() != null) {
            wrapper.eq(ProductPO::getCategoryId, query.categoryId());
        }
        if (query.hasBrand()) {
            wrapper.eq(ProductPO::getBrand, query.brand());
        }
        if (query.hasPriceRange()) {
            wrapper.inSql(ProductPO::getId, minPriceFilterSql(query));
        }
        applySort(wrapper, query);

        var entityPage = productMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(query.page(), query.size()), wrapper);

        if (entityPage.getRecords().isEmpty()) {
            return SearchPage.empty(query.page(), query.size());
        }

        Map<Long, Integer> priceMap = getMinPrices(entityPage.getRecords());
        List<ProductSearchHit> hits = entityPage.getRecords().stream()
                .map(po -> toHit(po, priceMap.getOrDefault(po.getId(), 0)))
                .toList();
        return new SearchPage<>(hits, entityPage.getTotal(), query.page(), query.size());
    }

    /**
     * facet 聚合（SE6）—— DB LIKE 链无聚合能力（facet 是 ES 能力），恒返回空对象，
     * C 端过滤 UI 按无 facet 降级展示，不报错。
     */
    @Override
    public SearchFacets facets(SearchQuery query) {
        return SearchFacets.empty();
    }

    @Override
    public List<ProductSearchHit> vectorSearch(double[] embedding, int size) {
        // DB 不支持向量搜索，返回空列表（降级处理）
        return Collections.emptyList();
    }

    @Override
    public Map<String, Object> reindex() {
        return Map.of("status", "ok", "count", 0, "esEnabled", false);
    }

    @Override
    public void upsertVectorDoc(ProductVectorDoc doc) {
        // DB 链无向量能力，摄取为空操作（向量是 ES 能力，仅 ES 链路实现）
    }

    @Override
    public Optional<double[]> findVectorById(Long productId) {
        // DB 链无向量能力，恒视为无向量（fail-open）
        return Optional.empty();
    }

    /**
     * 排序（SE1）：price=在售 SKU 最低价升序（关联子查询）；newest=publish_time 降序；
     * sales/relevance=销量降序（relevance 为历史现行为：LIKE 无相关度打分）。
     */
    private void applySort(LambdaQueryWrapper<ProductPO> wrapper, SearchQuery query) {
        switch (query.sortOrDefault()) {
            case PRICE -> wrapper.last(MIN_PRICE_ORDER_ASC);
            case NEWEST -> wrapper.orderByDesc(ProductPO::getPublishTime);
            default -> wrapper.orderByDesc(ProductPO::getSoldCount);
        }
    }

    /**
     * 最低价区间过滤子查询（SE1）：{@code HAVING MIN(price)} 过滤「在售 SKU 最低价落在区间内」
     * 的商品，与 ES 链 doc.price（最低价）区间过滤同口径。
     * minPrice/maxPrice 为 Integer（Spring 绑定拒绝非数字入参），拼接无注入面。
     */
    private String minPriceFilterSql(SearchQuery query) {
        List<String> conditions = new ArrayList<>(2);
        if (query.minPrice() != null) {
            conditions.add("MIN(price) >= " + query.minPrice());
        }
        if (query.maxPrice() != null) {
            conditions.add("MIN(price) <= " + query.maxPrice());
        }
        return "SELECT product_id FROM sku WHERE status = " + STATUS_ON_SALE
                + " GROUP BY product_id HAVING " + String.join(" AND ", conditions);
    }

    /** 在售 SKU 最低价（按商品分组取最小）。 */
    private Map<Long, Integer> getMinPrices(List<ProductPO> products) {
        if (products.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = products.stream().map(ProductPO::getId).toList();

        List<SkuPO> skus = skuMapper.selectList(
                new LambdaQueryWrapper<SkuPO>()
                        .in(SkuPO::getProductId, ids)
                        .eq(SkuPO::getStatus, STATUS_ON_SALE));

        return skus.stream()
                .collect(Collectors.groupingBy(
                        SkuPO::getProductId,
                        Collectors.collectingAndThen(
                                Collectors.minBy(Comparator.comparingInt(SkuPO::getPrice)),
                                opt -> opt.map(SkuPO::getPrice).orElse(0))));
    }

    private static ProductSearchHit toHit(ProductPO po, int price) {
        return new ProductSearchHit(po.getId(), po.getProductId(), po.getCategoryId(), po.getName(),
                po.getDescription(), po.getBrand(), po.getMainImage(), price, po.getSoldCount());
    }
}
