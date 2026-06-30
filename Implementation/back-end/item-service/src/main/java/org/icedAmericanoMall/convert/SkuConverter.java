package org.icedAmericanoMall.convert;

import org.icedAmericanoMall.domain.entity.SkuEntity;
import org.icedAmericanoMall.domain.vo.SkuVO;
import org.icedAmericanoMall.dto.SkuDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SkuConverter {

    SkuVO entityToVO(SkuEntity entity);

    List<SkuVO> entitiesToVOs(List<SkuEntity> entities);
    @Mapping(target = "sellerId", ignore = true)
    @Mapping(target = "productName", ignore = true)
    SkuDTO entityToDTO(SkuEntity entity);

    List<SkuDTO> entitiesToDTOs(List<SkuEntity> entities);
}
