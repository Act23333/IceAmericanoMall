package org.icedAmericanoMall.convert;

import org.icedAmericanoMall.domain.entity.CategoryEntity;
import org.icedAmericanoMall.domain.vo.CategoryTreeVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoryConverter {

    CategoryTreeVO entityToTreeVO(CategoryEntity entity);
}
