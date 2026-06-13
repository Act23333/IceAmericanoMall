package org.icedAmericanoMall.convert;

import org.icedAmericanoMall.domain.entity.ProductEntity;
import org.icedAmericanoMall.domain.vo.ProductVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductConverter {

    @Mapping(target = "skus", ignore = true)
    ProductVO entityToVO(ProductEntity entity);
}
