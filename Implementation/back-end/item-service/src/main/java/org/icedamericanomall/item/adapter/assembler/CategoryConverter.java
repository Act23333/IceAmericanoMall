package org.icedamericanomall.item.adapter.assembler;

import org.icedamericanomall.item.adapter.request.CategoryReq;
import org.icedamericanomall.item.adapter.response.CategoryTreeVO;
import org.icedamericanomall.item.adapter.response.CategoryVO;
import org.icedamericanomall.item.domain.model.category.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoryConverter {

    /** 聚合 → TreeVO: children 由应用层递归构建，MapStruct 无需处理。 */
    @Mapping(target = "children", ignore = true)
    CategoryTreeVO toTreeVO(Category category);

    /** 聚合 → VO（新增/修改接口回显）。 */
    CategoryVO toVO(Category category);

    /** 请求 → 聚合: id/createTime/updateTime 由持久层填充，不来自请求体。 */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    Category toDomain(CategoryReq req);
}