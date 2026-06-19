package org.icedAmericanoMall.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.ReviewEntity;
import org.icedAmericanoMall.mapper.ReviewMapper;
import org.noLazy.common.domain.Result;
import org.noLazy.common.exception.BizException;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/item/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewMapper reviewMapper;

    @PostMapping
    public Result<ReviewEntity> create(@RequestBody ReviewEntity entity) {
        entity.setUserId(UserContext.getUser());
        // Check duplicate: one review per order per user
        Long count = reviewMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReviewEntity>()
                        .eq(ReviewEntity::getOrderId, entity.getOrderId())
                        .eq(ReviewEntity::getUserId, entity.getUserId()));
        if (count > 0) {
            throw new BizException(org.noLazy.common.enums.ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "该订单已评价");
        }
        reviewMapper.insert(entity);
        return Result.ok(entity);
    }

    @GetMapping("/product/{productId}")
    public Result<IPage<ReviewEntity>> listByProduct(@PathVariable Long productId,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "10") int size) {
        var result = reviewMapper.selectPage(new Page<>(page, size),
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReviewEntity>()
                        .eq(ReviewEntity::getProductId, productId)
                        .orderByDesc(ReviewEntity::getCreateTime));
        return Result.ok(result);
    }
}
