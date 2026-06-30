package org.icedAmericanoMall.convert;

import org.icedAmericanoMall.domain.entity.CategoryEntity;
import org.icedAmericanoMall.domain.vo.CategoryTreeVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoryConverter {

    @Mapping(target = "children", ignore = true)   // 告诉 MapStruct，这个属性我来处理
    CategoryTreeVO entityToTreeVO(CategoryEntity entity);
}
