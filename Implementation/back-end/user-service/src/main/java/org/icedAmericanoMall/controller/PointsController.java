package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.service.PointsService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/user/points")
@RequiredArgsConstructor
public class PointsController {

    private final PointsService pointsService;

    @GetMapping("/balance")
    public Result<Map<String, Object>> getBalance() {
        Long userId = UserContext.getUser();
        return Result.ok(Map.of(
                "userId", userId,
                "balance", pointsService.getBalance(userId)));
    }

    @GetMapping("/history")
    public Result<?> getHistory(@RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.getUser();
        return Result.ok(pointsService.getHistory(userId, page, size));
    }
}
