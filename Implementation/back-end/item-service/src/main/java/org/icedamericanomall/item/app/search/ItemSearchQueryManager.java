package org.icedamericanomall.item.app.search;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.dto.SpuSearchItemCO;
import org.icedamericanomall.dto.SpuSearchPageCO;
import org.icedamericanomall.dto.SpuSearchQueryCO;
import org.icedamericanomall.item.domain.model.PageResult;
import org.icedamericanomall.item.domain.model.spu.SpuSearchCard;
import org.icedamericanomall.item.domain.model.spu.SpuSearchCriteria;
import org.icedamericanomall.item.domain.repository.SpuRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商品检索出口编排（应用层，ADR-052）—— search-service 的 LIKE 降级检索 / 联想 /
 * ES 重建数据源 / publishTime 回填统一经此出口取数（商品数据面归 item 域所有，
 * search 直读已退出）。
 *
 * <p>入参夹取：页码 ≥1、页长 1..100、联想 limit 1..50（内部端点也做防御性限幅，
 * DB 压力不依赖调用方自律）；排序词汇透传（SQL 侧白名单化，非白名单回落销量降序）。
 */
@Component
@RequiredArgsConstructor
public class ItemSearchQueryManager {

    /** 检索页长上限（内部端点防御性限幅）。 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 联想词条数上限。 */
    private static final int MAX_SUGGEST_LIMIT = 50;

    private final SpuRepository spuRepository;

    /** 商品检索分页（LIKE 降级链路数据源）。 */
    public SpuSearchPageCO searchPage(SpuSearchQueryCO query) {
        SpuSearchCriteria criteria = toCriteria(query == null ? new SpuSearchQueryCO() : query);
        PageResult<SpuSearchCard> result = spuRepository.searchPage(criteria);
        return toPageCO(result);
    }

    /** 在售商品全量导出分页（ES 重建数据源，按 id 升序稳定遍历）。 */
    public SpuSearchPageCO exportPage(int page, int size) {
        PageResult<SpuSearchCard> result = spuRepository.exportPage(
                Math.max(1, page), clamp(size, 1, MAX_PAGE_SIZE));
        return toPageCO(result);
    }

    /** 商品名前缀联想（在售，销量降序，去重截断）。 */
    public List<String> suggest(String prefix, int limit) {
        if (prefix == null || prefix.isBlank() || limit <= 0) {
            return List.of();
        }
        return spuRepository.suggestNames(prefix.trim(), clamp(limit, 1, MAX_SUGGEST_LIMIT));
    }

    /** 单商品检索卡片（ES 摄取 publishTime 回填用）；不存在返回 null。 */
    public SpuSearchItemCO detail(Long id) {
        if (id == null) {
            return null;
        }
        SpuSearchCard card = spuRepository.findSearchCardById(id);
        return card == null ? null : toItemCO(card);
    }

    private static SpuSearchCriteria toCriteria(SpuSearchQueryCO query) {
        return new SpuSearchCriteria(
                query.getKeyword(), query.getCategoryId(), query.getBrand(),
                query.getMinPrice(), query.getMaxPrice(), query.getSort(),
                Math.max(1, query.getPage()), clamp(query.getSize(), 1, MAX_PAGE_SIZE));
    }

    private static SpuSearchPageCO toPageCO(PageResult<SpuSearchCard> result) {
        SpuSearchPageCO co = new SpuSearchPageCO();
        co.setTotal(result.total());
        co.setRecords(result.records().stream().map(ItemSearchQueryManager::toItemCO).toList());
        return co;
    }

    private static SpuSearchItemCO toItemCO(SpuSearchCard card) {
        SpuSearchItemCO co = new SpuSearchItemCO();
        co.setId(card.id());
        co.setSpuId(card.spuId());
        co.setCategoryId(card.categoryId());
        co.setName(card.name());
        co.setDescription(card.description());
        co.setBrand(card.brand());
        co.setMainImage(card.mainImage());
        co.setMinPrice(card.minPrice());
        co.setSoldCount(card.soldCount());
        co.setPublishTime(card.publishTime());
        return co;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
