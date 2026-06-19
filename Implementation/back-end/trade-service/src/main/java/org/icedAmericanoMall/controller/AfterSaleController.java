package org.icedAmericanoMall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.AfterSaleEntity;
import org.icedAmericanoMall.mapper.AfterSaleMapper;
import org.noLazy.common.domain.Result;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 售后申请 — 用户端 */
@RestController
@RequestMapping("/api/after-sale")
@RequiredArgsConstructor
public class AfterSaleController {

    private final AfterSaleMapper afterSaleMapper;

    @PostMapping
    public Result<AfterSaleEntity> apply(@RequestBody AfterSaleEntity entity) {
        entity.setUserId(UserContext.getUser());
        entity.setStatus(1);
        // Check duplicate
        Long count = afterSaleMapper.selectCount(
                new LambdaQueryWrapper<AfterSaleEntity>()
                        .eq(AfterSaleEntity::getOrderNo, entity.getOrderNo())
                        .eq(AfterSaleEntity::getUserId, entity.getUserId()));
        if (count > 0) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已有售后申请");
        afterSaleMapper.insert(entity);
        return Result.ok(entity);
    }

    @GetMapping
    public Result<List<AfterSaleEntity>> myList() {
        return Result.ok(afterSaleMapper.selectList(
                new LambdaQueryWrapper<AfterSaleEntity>()
                        .eq(AfterSaleEntity::getUserId, UserContext.getUser())
                        .orderByDesc(AfterSaleEntity::getCreateTime)));
    }
}
