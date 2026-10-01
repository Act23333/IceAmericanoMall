package org.icedamericanomall.item.convert;

import org.icedamericanomall.item.domain.entity.SkuEntity;
import org.icedamericanomall.item.domain.vo.SkuVO;
import org.icedamericanomall.dto.SkuCO;
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
     * entity → DTO: SkuCO.id (Long) ← SkuEntity.id (Long) 自动匹配技术主键。
     * sellerId / productName 在 SkuEntity 中不存在，需调用方从 ProductEntity 补齐。
     */
    @Mapping(target = "sellerId", ignore = true)
    @Mapping(target = "productName", ignore = true)
    SkuCO entityToDTO(SkuEntity entity);

    List<SkuCO> entitiesToDTOs(List<SkuEntity> entities);
}
