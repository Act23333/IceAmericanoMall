package org.icedamericanomall.search.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.search.domain.gateway.ProductSearchGateway;
import org.icedamericanomall.search.domain.model.search.ProductSearchHit;
import org.icedamericanomall.search.domain.model.search.ProductVectorDoc;
import org.icedamericanomall.search.domain.model.search.SearchPage;
import org.icedamericanomall.search.domain.model.search.SearchQuery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DB LIKE 检索网关 —— ES 未启用时的缺省实现（MyBatis-Plus lambdaQuery 参数化，杜绝 SQL 注入）。
 *
 * <p>向量检索与向量摄取均不支持（向量是 ES 能力）：{@code vectorSearch} 恒返回空列表、
 * {@code upsertVectorDoc} 为空操作、{@code findVectorById} 恒 empty；reindex 不适用（返回未启用标记）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "false", matchIfMissing = true)
public class DbProductSearchGateway implements ProductSearchGateway {

    /** 在售状态（item-service 商品域的口径，此处只读镜像表）。 */
    private static final int STATUS_ON_SALE = 1;

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
        wrapper.orderByDesc(ProductPO::getSoldCount);

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
