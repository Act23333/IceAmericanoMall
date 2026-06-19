package org.icedAmericanoMall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.HomeConfigEntity;
import org.icedAmericanoMall.mapper.HomeConfigMapper;
import org.noLazy.common.domain.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 首页配置 — 公开接口 */
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeConfigMapper homeConfigMapper;

    @GetMapping("/config")
    public Result<List<HomeConfigEntity>> config() {
        return Result.ok(homeConfigMapper.selectList(
                new LambdaQueryWrapper<HomeConfigEntity>()
                        .eq(HomeConfigEntity::getStatus, 1)
                        .orderByAsc(HomeConfigEntity::getSortOrder)));
    }
}
