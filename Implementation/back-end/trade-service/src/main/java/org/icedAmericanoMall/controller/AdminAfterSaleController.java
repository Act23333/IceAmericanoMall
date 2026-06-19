package org.icedAmericanoMall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.AfterSaleEntity;
import org.icedAmericanoMall.mapper.AfterSaleMapper;
import org.noLazy.common.domain.Result;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 售后管理 — 管理员 */
@RestController
@RequestMapping("/api/admin/after-sale")
@RequiredArgsConstructor
public class AdminAfterSaleController {

    private final AfterSaleMapper afterSaleMapper;

    @GetMapping
    public Result<List<AfterSaleEntity>> list(@RequestParam(defaultValue = "1") Integer status) {
        return Result.ok(afterSaleMapper.selectList(
                new LambdaQueryWrapper<AfterSaleEntity>()
                        .eq(status != null, AfterSaleEntity::getStatus, status)
                        .orderByDesc(AfterSaleEntity::getCreateTime)));
    }

    @PutMapping("/{id}/review")
    public Result<?> review(@PathVariable Long id, @RequestParam Integer status,
                             @RequestParam(required = false) String remark) {
        AfterSaleEntity entity = afterSaleMapper.selectById(id);
        if (entity == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "售后申请不存在");
        entity.setStatus(status);
        entity.setAdminRemark(remark);
        afterSaleMapper.updateById(entity);
        return Result.ok();
    }
}
