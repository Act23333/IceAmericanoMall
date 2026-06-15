package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class SignController {

    private final UserSignService userSignService;

    @PostMapping
    public Result<Map<String, Object>> sign() {
        Long userId = UserContext.getUser();
        userSignService.sign(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("signed", true);
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
