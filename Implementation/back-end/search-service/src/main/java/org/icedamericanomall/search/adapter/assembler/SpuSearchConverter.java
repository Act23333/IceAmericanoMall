package org.icedamericanomall.search.adapter.assembler;

import org.icedamericanomall.search.adapter.response.SpuSearchVO;
import org.icedamericanomall.search.adapter.response.SearchPageVO;
import org.icedamericanomall.search.adapter.response.SearchFacetsVO;
import org.icedamericanomall.search.domain.model.search.SpuSearchHit;
import org.icedamericanomall.search.domain.model.search.SearchFacets;
import org.icedamericanomall.search.domain.model.search.SearchPage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 搜索结果转换器（领域命中 → 对外 VO / 自持分页信封）。
 *
 * <p>HTTP 契约核心字段不变（records/total/size/current/pages），外层改用自持
 * {@link SearchPageVO}（ADR-052：原 MyBatis-Plus {@code Page} 为框架类型泄漏，随直读退出一并清理）。
 * {@code price} 在领域命中中由检索实现按最低在售 SKU 价格补齐；
 * {@code SpuDocument.embedding} 为索引内部字段，不进入对外 VO。
 *
 * <p><b>{@code name → spuName}（契约 §1.8④，2026-10-18 裁决「甲」）</b>：领域读模型
 * {@link SpuSearchHit} 的字段名保持 {@code name}（领域命名空间，与 ES 索引字段
 * {@code SpuDocument.name} 同源、不出报文），线上键由 {@link SpuSearchVO#getSpuName()}
 * 经全局 {@code SNAKE_CASE} 渲染为 {@code spu_name} —— 这层「领域名 ≠ 线上名」的映射必须
 * 显式声明，不能靠同名巧合（同名巧合正是本批要消灭的静默失效面）。
 */
@Mapper(componentModel = "spring")
public interface SpuSearchConverter {

    /** 领域命中 → 对外 VO（{@code name} → {@code spuName}，线上键 {@code spu_name}）。 */
    @Mapping(source = "name", target = "spuName")
    SpuSearchVO hitToVO(SpuSearchHit hit);

    List<SpuSearchVO> hitsToVOs(List<SpuSearchHit> hits);

    /** 领域分页 → 自持分页信封（核心字段与历史 JSON 一致）。 */
    default SearchPageVO<SpuSearchVO> toPage(SearchPage<SpuSearchHit> domainPage) {
        return SearchPageVO.of(hitsToVOs(domainPage.records()), domainPage.total(),
                domainPage.page(), domainPage.size());
    }

    /** 领域 facet（SE6）→ facet 视图（品牌/价格档位计数，min/max 单位分）。 */
    SearchFacetsVO toVO(SearchFacets facets);
}
