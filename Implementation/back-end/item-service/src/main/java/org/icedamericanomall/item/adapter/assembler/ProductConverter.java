package org.icedamericanomall.item.adapter.assembler;

import org.icedamericanomall.item.adapter.response.ProductVO;
import org.icedamericanomall.item.domain.model.product.Product;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductConverter {

    ProductVO toVO(Product product);
}