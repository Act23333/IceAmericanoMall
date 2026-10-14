package org.icedamericanomall.search.adapter.assembler;

import org.icedamericanomall.search.adapter.response.ProductSearchVO;
import org.icedamericanomall.search.adapter.response.SearchPageVO;
import org.icedamericanomall.search.adapter.response.SearchFacetsVO;
import org.icedamericanomall.search.domain.model.search.ProductSearchHit;
import org.icedamericanomall.search.domain.model.search.SearchFacets;
import org.icedamericanomall.search.domain.model.search.SearchPage;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 搜索结果转换器（领域命中 → 对外 VO / 自持分页信封）。
 *
 * <p>HTTP 契约核心字段不变（records/total/size/current/pages），外层改用自持
 * {@link SearchPageVO}（ADR-052：原 MyBatis-Plus {@code Page} 为框架类型泄漏，随直读退出一并清理）。
 * {@code price} 在领域命中中由检索实现按最低在售 SKU 价格补齐；
 * {@code ProductDocument.embedding} 为索引内部字段，不进入对外 VO。
 */
@Mapper(componentModel = "spring")
public interface ProductSearchConverter {

    ProductSearchVO hitToVO(ProductSearchHit hit);

    List<ProductSearchVO> hitsToVOs(List<ProductSearchHit> hits);

    /** 领域分页 → 自持分页信封（核心字段与历史 JSON 一致）。 */
    default SearchPageVO<ProductSearchVO> toPage(SearchPage<ProductSearchHit> domainPage) {
        return SearchPageVO.of(hitsToVOs(domainPage.records()), domainPage.total(),
                domainPage.page(), domainPage.size());
    }

    /** 领域 facet（SE6）→ facet 视图（品牌/价格档位计数，min/max 单位分）。 */
    SearchFacetsVO toVO(SearchFacets facets);
}
