package org.icedAmericanoMall.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.FavoriteEntity;
import org.icedAmericanoMall.mapper.FavoriteMapper;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteMapper favoriteMapper;

    @PostMapping
    public Result<?> add(@RequestParam Long productId) {
        Long userId = UserContext.getUser();
        var q = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FavoriteEntity>()
                .eq(FavoriteEntity::getUserId, userId)
                .eq(FavoriteEntity::getProductId, productId);
        if (favoriteMapper.selectCount(q) == 0) {
            FavoriteEntity entity = new FavoriteEntity();
            entity.setUserId(userId);
            entity.setProductId(productId);
            favoriteMapper.insert(entity);
        }
        return Result.ok();
    }

    @DeleteMapping
    public Result<?> remove(@RequestParam Long productId) {
        Long userId = UserContext.getUser();
        favoriteMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FavoriteEntity>()
                .eq(FavoriteEntity::getUserId, userId)
                .eq(FavoriteEntity::getProductId, productId));
        return Result.ok();
    }

    @GetMapping
    public Result<IPage<FavoriteEntity>> list(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.getUser();
        var result = favoriteMapper.selectPage(new Page<>(page, size),
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FavoriteEntity>()
                        .eq(FavoriteEntity::getUserId, userId)
                        .orderByDesc(FavoriteEntity::getCreateTime));
        return Result.ok(result);
    }
}
