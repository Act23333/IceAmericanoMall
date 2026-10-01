package org.icedamericanomall.item.convert;

import org.icedamericanomall.item.domain.entity.CategoryEntity;
import org.icedamericanomall.item.domain.vo.CategoryTreeVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoryConverter {

    /**
     * entity → TreeVO: children 由 service 层递归构建，MapStruct 无需处理。
     */
    @Mapping(target = "children", ignore = true)
    CategoryTreeVO entityToTreeVO(CategoryEntity entity);
}
