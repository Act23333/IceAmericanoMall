package org.icedamericanomall.user.app.favorite;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.adapter.response.FavoriteVO;
import org.icedamericanomall.user.infrastructure.persistence.favorite.FavoriteDO;
import org.icedamericanomall.user.infrastructure.persistence.favorite.FavoriteMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品收藏（应用层）—— 去重收藏（{userId, productId} 幂等）、取消收藏、分页查询。
 */
@Component
@RequiredArgsConstructor
public class FavoriteManager {

    private final FavoriteMapper favoriteMapper;

    /** 收藏（幂等：已存在不重复插入）。 */
    @Transactional(rollbackFor = Exception.class)
    public void add(Long userId, Long productId) {
        long count = favoriteMapper.selectCount(new LambdaQueryWrapper<FavoriteDO>()
                .eq(FavoriteDO::getUserId, userId)
                .eq(FavoriteDO::getProductId, productId));
        if (count == 0) {
            FavoriteDO entity = new FavoriteDO();
            entity.setUserId(userId);
            entity.setProductId(productId);
            favoriteMapper.insert(entity);
        }
    }

    /** 取消收藏。 */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId, Long productId) {
        favoriteMapper.delete(new LambdaQueryWrapper<FavoriteDO>()
                .eq(FavoriteDO::getUserId, userId)
                .eq(FavoriteDO::getProductId, productId));
    }

    /** 分页查询收藏（按收藏时间倒序）。 */
    public IPage<FavoriteVO> pageByUser(Long userId, int pageNum, int size) {
        IPage<FavoriteDO> result = favoriteMapper.selectPage(new Page<>(pageNum, size),
                new LambdaQueryWrapper<FavoriteDO>()
                        .eq(FavoriteDO::getUserId, userId)
                        .orderByDesc(FavoriteDO::getCreateTime));
        return result.convert(FavoriteVO::from);
    }
}
