package org.icedamericanomall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.convert.FavoriteConverter;
import org.icedamericanomall.domain.entity.FavoriteEntity;
import org.icedamericanomall.domain.vo.FavoriteVO;
import org.icedamericanomall.mapper.FavoriteMapper;
import org.icedamericanomall.service.FavoriteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品收藏服务实现。
 */
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl extends ServiceImpl<FavoriteMapper, FavoriteEntity> implements FavoriteService {

    private final FavoriteConverter favoriteConverter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(Long userId, Long productId) {
        long count = lambdaQuery()
                .eq(FavoriteEntity::getUserId, userId)
                .eq(FavoriteEntity::getProductId, productId)
                .count();
        if (count == 0) {
            FavoriteEntity entity = new FavoriteEntity();
            entity.setUserId(userId);
            entity.setProductId(productId);
            save(entity);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId, Long productId) {
        remove(new LambdaQueryWrapper<FavoriteEntity>()
                .eq(FavoriteEntity::getUserId, userId)
                .eq(FavoriteEntity::getProductId, productId));
    }

    @Override
    public IPage<FavoriteVO> pageByUser(Long userId, int page, int size) {
        IPage<FavoriteEntity> result = lambdaQuery()
                .eq(FavoriteEntity::getUserId, userId)
                .orderByDesc(FavoriteEntity::getCreateTime)
                .page(new Page<>(page, size));
        return result.convert(favoriteConverter::toVO);
    }
}
