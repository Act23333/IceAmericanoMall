package org.icedamericanomall.convert;

import org.icedamericanomall.domain.entity.SellerEntity;
import org.icedamericanomall.domain.vo.SellerVO;
import org.mapstruct.Mapper;

/**
 * 商家 Entity → VO 转换器。
 */
@Mapper(componentModel = "spring")
public interface SellerConverter {

    SellerVO toVO(SellerEntity entity);
}
