package org.icedamericanomall.user.convert;

import org.icedamericanomall.user.domain.entity.SellerEntity;
import org.icedamericanomall.user.domain.vo.SellerVO;
import org.mapstruct.Mapper;

/**
 * 商家 Entity → VO 转换器。
 */
@Mapper(componentModel = "spring")
public interface SellerConverter {

    SellerVO toVO(SellerEntity entity);
}
