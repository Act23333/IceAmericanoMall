package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.HomeConfigEntity;
import org.icedAmericanoMall.service.HomeConfigService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/home")
@RequiredArgsConstructor
public class AdminHomeController {

    private final HomeConfigService homeConfigService;

    @PostMapping
    public Result<HomeConfigEntity> create(@RequestBody HomeConfigEntity entity) {
        homeConfigService.save(entity);
        return Result.ok(entity);
    }

    @PutMapping("/{id}")
    public Result<HomeConfigEntity> update(@PathVariable Long id, @RequestBody HomeConfigEntity entity) {
        if (homeConfigService.getById(id) == null)
            throw new BizException(ErrorCode.USER_NOT_FOUND, "配置不存在");
        entity.setId(id);
        homeConfigService.updateById(entity);
        return Result.ok(entity);
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        homeConfigService.removeById(id);
        return Result.ok();
    }

    @GetMapping
    public Result<?> list() {
        return Result.ok(homeConfigService.list());
    }
}
