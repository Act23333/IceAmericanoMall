package org.icedamericanomall.convert;

import org.icedamericanomall.domain.entity.FavoriteEntity;
import org.icedamericanomall.domain.vo.FavoriteVO;
import org.mapstruct.Mapper;

/**
 * 商品收藏 Entity → VO 转换器。
 */
@Mapper(componentModel = "spring")
public interface FavoriteConverter {

    FavoriteVO toVO(FavoriteEntity entity);
}
