package org.icedAmericanoMall.convert;

import org.icedAmericanoMall.domain.entity.SellerEntity;
import org.icedAmericanoMall.domain.vo.SellerVO;
import org.mapstruct.Mapper;

/**
 * 商家 Entity → VO 转换器。
 */
@Mapper(componentModel = "spring")
public interface SellerConverter {

    SellerVO toVO(SellerEntity entity);
}
