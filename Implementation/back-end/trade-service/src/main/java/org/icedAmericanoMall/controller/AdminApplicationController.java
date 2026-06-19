package org.icedAmericanoMall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.SellerApplicationEntity;
import org.icedAmericanoMall.mapper.SellerApplicationMapper;
import org.noLazy.common.domain.Result;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 商家入驻审核 — 管理员 */
@RestController
@RequestMapping("/api/admin/application")
@RequiredArgsConstructor
public class AdminApplicationController {

    private final SellerApplicationMapper appMapper;

    @GetMapping
    public Result<List<SellerApplicationEntity>> list(@RequestParam(defaultValue = "0") Integer status) {
        return Result.ok(appMapper.selectList(
                new LambdaQueryWrapper<SellerApplicationEntity>()
                        .eq(SellerApplicationEntity::getStatus, status)
                        .orderByDesc(SellerApplicationEntity::getCreateTime)));
    }

    @PutMapping("/{id}/review")
    public Result<?> review(@PathVariable Long id, @RequestParam Integer status,
                             @RequestParam(required = false) String remark) {
        SellerApplicationEntity app = appMapper.selectById(id);
        if (app == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "申请不存在");
        app.setStatus(status);
        app.setAdminRemark(remark);
        appMapper.updateById(app);
        return Result.ok();
    }
}
