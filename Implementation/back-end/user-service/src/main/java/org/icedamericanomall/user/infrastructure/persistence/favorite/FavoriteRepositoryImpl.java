package org.icedamericanomall.user.infrastructure.persistence.favorite;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.domain.model.favorite.Favorite;
import org.icedamericanomall.user.domain.repository.FavoriteRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 商品收藏仓储实现（基础设施层）。
 *
 * <p>业务键 = {@code userId + spuId}：新增先按该键计数、为 0 才插入（去重语义，
 * 与 {@code (user_id, spu_id)} 唯一键形成双层保障）；取消/查询均携带 userId。
 */
@Repository
@RequiredArgsConstructor
public class FavoriteRepositoryImpl implements FavoriteRepository {

    private final FavoriteMapper favoriteMapper;

    @Override
    public void addIfAbsent(Favorite favorite) {
        long count = favoriteMapper.selectCount(new LambdaQueryWrapper<FavoritePO>()
                .eq(FavoritePO::getUserId, favorite.getUserId())
                .eq(FavoritePO::getSpuId, favorite.getSpuId()));
        if (count == 0) {
            favoriteMapper.insert(FavoriteConverter.toDataObject(favorite));
        }
    }

    @Override
    public int removeByUserIdAndSpuId(Long userId, Long spuId) {
        return favoriteMapper.delete(new LambdaQueryWrapper<FavoritePO>()
                .eq(FavoritePO::getUserId, userId)
                .eq(FavoritePO::getSpuId, spuId));
    }

    @Override
    public long countByUserId(Long userId) {
        return favoriteMapper.selectCount(new LambdaQueryWrapper<FavoritePO>()
                .eq(FavoritePO::getUserId, userId));
    }

    @Override
    public List<Favorite> findPageDescByUserId(Long userId, long pageNum, long pageSize) {
        IPage<FavoritePO> result = favoriteMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<FavoritePO>()
                        .eq(FavoritePO::getUserId, userId)
                        .orderByDesc(FavoritePO::getCreateTime));
        return result.getRecords().stream()
                .map(FavoriteConverter::toDomain)
                .toList();
    }
}
