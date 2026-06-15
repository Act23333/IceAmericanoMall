package org.icedAmericanoMall.convert;

import javax.annotation.processing.Generated;
import org.icedAmericanoMall.domain.entity.ProductEntity;
import org.icedAmericanoMall.domain.vo.ProductVO;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-06-15T14:49:24+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Ubuntu)"
)
@Component
public class ProductConverterImpl implements ProductConverter {

    @Override
    public ProductVO entityToVO(ProductEntity entity) {
        if ( entity == null ) {
            return null;
        }

        ProductVO productVO = new ProductVO();

        productVO.setId( entity.getId() );
        productVO.setProductId( entity.getProductId() );
        productVO.setSellerId( entity.getSellerId() );
        productVO.setCategoryId( entity.getCategoryId() );
        productVO.setName( entity.getName() );
        productVO.setMainImage( entity.getMainImage() );
        productVO.setDescription( entity.getDescription() );
        productVO.setBrand( entity.getBrand() );
        productVO.setSoldCount( entity.getSoldCount() );
        productVO.setCommentCount( entity.getCommentCount() );
        productVO.setStatus( entity.getStatus() );
        productVO.setPublishTime( entity.getPublishTime() );

        return productVO;
    }
}
