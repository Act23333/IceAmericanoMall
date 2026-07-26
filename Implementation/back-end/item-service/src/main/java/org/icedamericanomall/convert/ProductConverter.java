package org.icedamericanomall.convert;

import org.icedamericanomall.domain.entity.ProductEntity;
import org.icedamericanomall.domain.vo.ProductVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductConverter {

    /**
     * entity → VO: skus 在 entity 中不存在对应字段，由 service 层手动填充。
     */
    @Mapping(target = "skus", ignore = true)
    ProductVO entityToVO(ProductEntity entity);
}
