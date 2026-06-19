package org.icedAmericanoMall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.SellerApplicationEntity;
import org.icedAmericanoMall.mapper.SellerApplicationMapper;
import org.noLazy.common.domain.Result;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 商家入驻申请 — 用户端 */
@RestController
@RequestMapping("/api/seller/apply")
@RequiredArgsConstructor
public class SellerApplicationController {

    private final SellerApplicationMapper appMapper;

    @PostMapping
    public Result<SellerApplicationEntity> apply(@RequestBody SellerApplicationEntity entity) {
        Long userId = UserContext.getUser();
        Long count = appMapper.selectCount(
                new LambdaQueryWrapper<SellerApplicationEntity>()
                        .eq(SellerApplicationEntity::getUserId, userId)
                        .eq(SellerApplicationEntity::getStatus, 0));
        if (count > 0) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已有审核中的申请");
        entity.setUserId(userId);
        entity.setStatus(0);
        appMapper.insert(entity);
        return Result.ok(entity);
    }

    @GetMapping
    public Result<SellerApplicationEntity> myApplication() {
        return Result.ok(appMapper.selectOne(
                new LambdaQueryWrapper<SellerApplicationEntity>()
                        .eq(SellerApplicationEntity::getUserId, UserContext.getUser())
                        .orderByDesc(SellerApplicationEntity::getCreateTime)
                        .last("LIMIT 1")));
    }
}
