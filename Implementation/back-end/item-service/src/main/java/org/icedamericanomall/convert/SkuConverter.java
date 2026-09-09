package org.icedamericanomall.convert;

import org.icedamericanomall.domain.entity.SkuEntity;
import org.icedamericanomall.domain.vo.SkuVO;
import org.icedamericanomall.dto.SkuDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SkuConverter {

    /**
     * entity → VO: id/skuId 是正确的——
     * SkuVO.id (Long)  ←  SkuEntity.id (Long, 技术主键)
     * SkuVO.skuId (String)  ←  SkuEntity.skuId (String, 业务UUID)
     */
    SkuVO entityToVO(SkuEntity entity);

    List<SkuVO> entitiesToVOs(List<SkuEntity> entities);

    /**
     * entity → DTO: SkuDTO.id (Long) ← SkuEntity.id (Long) 自动匹配技术主键。
     * sellerId / productName 在 SkuEntity 中不存在，需调用方从 ProductEntity 补齐。
     */
    @Mapping(target = "sellerId", ignore = true)
    @Mapping(target = "productName", ignore = true)
    SkuDTO entityToDTO(SkuEntity entity);

    List<SkuDTO> entitiesToDTOs(List<SkuEntity> entities);
}
