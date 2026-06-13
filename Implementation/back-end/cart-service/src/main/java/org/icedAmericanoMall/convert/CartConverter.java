package org.icedAmericanoMall.convert;

import org.icedAmericanoMall.dto.response.CartItemResp;
import org.icedAmericanoMall.pojo.CartEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CartConverter {

    @Mapping(target = "productName", source = "productName")
    @Mapping(target = "spec", source = "skuSpec")
    @Mapping(target = "subTotal", expression = "java(entity.getPrice() != null ? entity.getPrice() * entity.getQuantity() : 0)")
    CartItemResp entityToResp(CartEntity entity);

    List<CartItemResp> entitiesToResps(List<CartEntity> entities);
}
