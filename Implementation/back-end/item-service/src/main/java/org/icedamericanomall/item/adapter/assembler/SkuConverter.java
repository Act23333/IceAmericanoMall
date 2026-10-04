package org.icedamericanomall.item.adapter.assembler;

import org.icedamericanomall.dto.SkuCO;
import org.icedamericanomall.item.adapter.response.SkuVO;
import org.icedamericanomall.item.domain.model.sku.Sku;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SkuConverter {

    SkuVO toVO(Sku sku);

    List<SkuVO> toVOs(List<Sku> skus);

    SkuCO toCO(Sku sku);

    List<SkuCO> toCOs(List<Sku> skus);
}