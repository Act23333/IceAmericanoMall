package org.icedAmericanoMall.controller;

import org.icedAmericanoMall.service.PointsService;
import org.icedAmericanoMall.service.UserSignService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 每日签到控制器 —— 基于 Redis Bitmap 实现。
 *
 * <pre>
 * Scenario: 每日签到成功
 *   Given 用户已登录且今天尚未签到
 *   When POST /user/sign
 *   Then Redis Bitmap 标记当天已签到
 *   And 返回签到状态（signed=true, 当月签到次数, 连续签到天数）
 *
 * Scenario: 重复签到幂等保护
 *   Given 今天已签到
 *   When POST /user/sign
 *   Then Bitmap 标记操作返回 false（已存在）
 *   And 仍然返回当月签到次数和连续天数
 *
 * Scenario: 查询签到状态
 *   Given 用户已登录
 *   When GET /user/sign/status
 *   Then 返回今日是否已签到、当月签到次数、连续签到天数
 * </pre>
 */
@RestController
@RequestMapping("/user/sign")
public class SignController {

    private final UserSignService userSignService;
    private final PointsService pointsService;

    public SignController(UserSignService userSignService, PointsService pointsService) {
        this.userSignService = userSignService;
        this.pointsService = pointsService;
    }

    @PostMapping
    public Result<Map<String, Object>> sign() {
        Long userId = UserContext.getUser();
        boolean isFirstToday = userSignService.sign(userId);

        // Award points on first sign-in of the day (consecutive day bonus)
        long earnedPoints = 0;
        if (isFirstToday) {
            long continuousDays = userSignService.countContinuousSign(userId);
            // Day 1: 10pts, Day 2: 15pts, Day 3+: 20pts per day (capped at 200pts/month)
            earnedPoints = Math.min(10 + (continuousDays - 1) * 5, 20);
            pointsService.addPoints(userId, (int) earnedPoints, 1, "每日签到 连续第" + continuousDays + "天");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("signed", isFirstToday);
        result.put("earnedPoints", earnedPoints);
        result.put("monthCount", userSignService.countCurrentMonthSign(userId));
        result.put("continuousDays", userSignService.countContinuousSign(userId));
        return Result.ok(result);
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Long userId = UserContext.getUser();
        Map<String, Object> result = new HashMap<>();
        result.put("todaySigned", userSignService.isTodaySigned(userId));
        result.put("monthCount", userSignService.countCurrentMonthSign(userId));
        result.put("continuousDays", userSignService.countContinuousSign(userId));
        return Result.ok(result);
    }
}
