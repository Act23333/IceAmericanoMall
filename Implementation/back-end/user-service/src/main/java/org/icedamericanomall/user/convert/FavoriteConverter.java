package org.icedamericanomall.user.convert;

import org.icedamericanomall.user.domain.entity.FavoriteEntity;
import org.icedamericanomall.user.domain.vo.FavoriteVO;
import org.mapstruct.Mapper;

/**
 * 商品收藏 Entity → VO 转换器。
 */
@Mapper(componentModel = "spring")
public interface FavoriteConverter {

    FavoriteVO toVO(FavoriteEntity entity);
}
