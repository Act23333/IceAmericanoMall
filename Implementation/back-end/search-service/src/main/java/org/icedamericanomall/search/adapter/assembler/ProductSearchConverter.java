package org.icedamericanomall.search.adapter.assembler;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedamericanomall.search.adapter.response.ProductSearchVO;
import org.icedamericanomall.search.adapter.response.SearchFacetsVO;
import org.icedamericanomall.search.domain.model.search.ProductSearchHit;
import org.icedamericanomall.search.domain.model.search.SearchFacets;
import org.icedamericanomall.search.domain.model.search.SearchPage;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 搜索结果转换器（领域命中 → 对外 VO / MP 分页信封）。
 *
 * <p>HTTP 契约保持历史形状：外层仍是 MyBatis-Plus {@code Page} 信封（records/total/size/current/pages），
 * 由 {@link #toPage(SearchPage)} 从领域分页装配；字段名与历史 JSON 完全一致。
 * {@code price} 在领域命中中由检索实现按最低在售 SKU 价格补齐；
 * {@code ProductDocument.embedding} 为索引内部字段，不进入对外 VO。
 */
@Mapper(componentModel = "spring")
public interface ProductSearchConverter {

    ProductSearchVO hitToVO(ProductSearchHit hit);

    List<ProductSearchVO> hitsToVOs(List<ProductSearchHit> hits);

    /** 领域分页 → MP 分页信封（保持 HTTP 契约不变）。 */
    default Page<ProductSearchVO> toPage(SearchPage<ProductSearchHit> domainPage) {
        Page<ProductSearchVO> voPage = new Page<>(domainPage.page(), domainPage.size(), domainPage.total());
        voPage.setRecords(hitsToVOs(domainPage.records()));
        return voPage;
    }

    /** 领域 facet（SE6）→ facet 视图（品牌/价格档位计数，min/max 单位分）。 */
    SearchFacetsVO toVO(SearchFacets facets);
}
