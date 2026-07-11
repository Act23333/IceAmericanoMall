package org.icedAmericanoMall.convert;

import org.icedAmericanoMall.domain.entity.FavoriteEntity;
import org.icedAmericanoMall.domain.vo.FavoriteVO;
import org.mapstruct.Mapper;

/**
 * 商品收藏 Entity → VO 转换器。
 */
@Mapper(componentModel = "spring")
public interface FavoriteConverter {

    FavoriteVO toVO(FavoriteEntity entity);
}
