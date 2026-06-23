package org.icedAmericanoMall.convert;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.icedAmericanoMall.dto.response.CartItemResp;
import org.icedAmericanoMall.pojo.CartEntity;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-06-22T00:46:07+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Ubuntu)"
)
@Component
public class CartConverterImpl implements CartConverter {

    @Override
    public CartItemResp entityToResp(CartEntity entity) {
        if ( entity == null ) {
            return null;
        }

        CartItemResp cartItemResp = new CartItemResp();

        cartItemResp.setProductName( entity.getProductName() );
        cartItemResp.setSpec( entity.getSkuSpec() );
        cartItemResp.setSkuId( entity.getSkuId() );
        cartItemResp.setImage( entity.getImage() );
        cartItemResp.setPrice( entity.getPrice() );
        cartItemResp.setQuantity( entity.getQuantity() );
        cartItemResp.setSelected( entity.getSelected() );

        cartItemResp.setSubTotal( entity.getPrice() != null ? entity.getPrice() * entity.getQuantity() : 0 );

        return cartItemResp;
    }

    @Override
    public List<CartItemResp> entitiesToResps(List<CartEntity> entities) {
        if ( entities == null ) {
            return null;
        }

        List<CartItemResp> list = new ArrayList<CartItemResp>( entities.size() );
        for ( CartEntity cartEntity : entities ) {
            list.add( entityToResp( cartEntity ) );
        }

        return list;
    }
}
