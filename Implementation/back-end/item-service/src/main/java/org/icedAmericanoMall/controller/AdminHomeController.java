package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.HomeConfigEntity;
import org.icedAmericanoMall.mapper.HomeConfigMapper;
import org.noLazy.common.domain.Result;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.web.bind.annotation.*;

/** 管理后台 — 首页装修 */
@RestController
@RequestMapping("/api/admin/home")
@RequiredArgsConstructor
public class AdminHomeController {

    private final HomeConfigMapper homeConfigMapper;

    @PostMapping
    public Result<HomeConfigEntity> create(@RequestBody HomeConfigEntity entity) {
        homeConfigMapper.insert(entity);
        return Result.ok(entity);
    }

    @PutMapping("/{id}")
    public Result<HomeConfigEntity> update(@PathVariable Long id, @RequestBody HomeConfigEntity entity) {
        HomeConfigEntity existing = homeConfigMapper.selectById(id);
        if (existing == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "配置不存在");
        entity.setId(id);
        homeConfigMapper.updateById(entity);
        return Result.ok(entity);
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        homeConfigMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping
    public Result<?> list() {
        return Result.ok(homeConfigMapper.selectList(null));
    }
}
