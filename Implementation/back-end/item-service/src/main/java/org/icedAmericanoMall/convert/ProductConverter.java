package org.icedAmericanoMall.convert;

import org.icedAmericanoMall.domain.entity.ProductEntity;
import org.icedAmericanoMall.domain.vo.ProductVO;
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
