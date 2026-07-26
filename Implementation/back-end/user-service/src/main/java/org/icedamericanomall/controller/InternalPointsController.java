package org.icedamericanomall.controller;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.service.PointsService;
import org.springframework.web.bind.annotation.*;

/**
 * 积分域内部接口 —— Feign 调用（原本混在 InternalUserController 中）。
 */
@RestController
@RequestMapping("/internal/points")
@RequiredArgsConstructor
public class InternalPointsController {

    private final PointsService pointsService;

    @PostMapping("/add")
    public long addPoints(@RequestParam Long userId, @RequestParam int points,
                          @RequestParam(defaultValue = "2") int type,
                          @RequestParam(defaultValue = "下单奖励") String source) {
        return pointsService.addPoints(userId, points, type, source);
    }
}
