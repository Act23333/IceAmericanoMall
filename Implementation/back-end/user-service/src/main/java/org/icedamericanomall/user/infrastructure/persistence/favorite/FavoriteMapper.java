package org.icedamericanomall.user.infrastructure.persistence.favorite;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.icedamericanomall.user.infrastructure.persistence.favorite.FavoriteDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FavoriteMapper extends BaseMapper<FavoriteDO> {
}
