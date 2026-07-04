package org.icedAmericanoMall.convert;

import org.icedAmericanoMall.domain.entity.ProductEntity;
import org.icedAmericanoMall.domain.vo.ProductSearchVO;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Entity → VO 转换。
 * 仅映射搜索结果需要的字段，price/sku 信息由 service 层补充。
 */
@Mapper(componentModel = "spring")
public interface ProductSearchConverter {

    ProductSearchVO entityToVO(ProductEntity entity);

    List<ProductSearchVO> entitiesToVOs(List<ProductEntity> entities);
}
