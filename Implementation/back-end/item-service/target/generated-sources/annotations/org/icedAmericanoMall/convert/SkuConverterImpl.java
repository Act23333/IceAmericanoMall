package org.icedAmericanoMall.convert;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.icedAmericanoMall.domain.entity.SkuEntity;
import org.icedAmericanoMall.domain.vo.SkuVO;
import org.icedAmericanoMall.dto.SkuDTO;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-06-15T23:59:43+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Ubuntu)"
)
@Component
public class SkuConverterImpl implements SkuConverter {

    @Override
    public SkuVO entityToVO(SkuEntity entity) {
        if ( entity == null ) {
            return null;
        }

        SkuVO skuVO = new SkuVO();

        skuVO.setId( entity.getId() );
        skuVO.setSkuId( entity.getSkuId() );
        skuVO.setProductId( entity.getProductId() );
        skuVO.setSpec( entity.getSpec() );
        skuVO.setPrice( entity.getPrice() );
        skuVO.setStock( entity.getStock() );
        skuVO.setImage( entity.getImage() );
        skuVO.setSoldCount( entity.getSoldCount() );
        skuVO.setStatus( entity.getStatus() );

        return skuVO;
    }

    @Override
    public List<SkuVO> entitiesToVOs(List<SkuEntity> entities) {
        if ( entities == null ) {
            return null;
        }

        List<SkuVO> list = new ArrayList<SkuVO>( entities.size() );
        for ( SkuEntity skuEntity : entities ) {
            list.add( entityToVO( skuEntity ) );
        }

        return list;
    }

    @Override
    public SkuDTO entityToDTO(SkuEntity entity) {
        if ( entity == null ) {
            return null;
        }

        SkuDTO skuDTO = new SkuDTO();

        if ( entity.getSkuId() != null ) {
            skuDTO.setSkuId( Long.parseLong( entity.getSkuId() ) );
        }
        skuDTO.setProductId( entity.getProductId() );
        skuDTO.setSpec( entity.getSpec() );
        skuDTO.setPrice( entity.getPrice() );
        skuDTO.setStock( entity.getStock() );
        skuDTO.setImage( entity.getImage() );
        skuDTO.setStatus( entity.getStatus() );

        return skuDTO;
    }

    @Override
    public List<SkuDTO> entitiesToDTOs(List<SkuEntity> entities) {
        if ( entities == null ) {
            return null;
        }

        List<SkuDTO> list = new ArrayList<SkuDTO>( entities.size() );
        for ( SkuEntity skuEntity : entities ) {
            list.add( entityToDTO( skuEntity ) );
        }

        return list;
    }
}
