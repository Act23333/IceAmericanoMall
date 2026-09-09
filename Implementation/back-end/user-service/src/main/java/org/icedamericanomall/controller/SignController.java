package org.icedamericanomall.controller;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.vo.SignResultVO;
import org.icedamericanomall.manager.SignManager;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

/**
 * 每日签到控制器 —— 基于 Redis Bitmap，连续签到积分递增。
 * 签到 + 积分发放的编排下沉至 {@link SignManager}。
 *
 * <pre>
 * Scenario: 每日签到成功
 *   Given 用户已登录且今天尚未签到
 *   When POST /user/sign
 *   Then Bitmap 标记当天已签到，按连续天数发放递增积分（10/15/20）
 *   And 返回 { signed, earnedPoints, monthCount, continuousDays }
 *
 * Scenario: 重复签到幂等保护
 *   Given 今天已签到
 *   When POST /user/sign
 *   Then signed=false, earnedPoints=0，仍返回统计
 * </pre>
 */
@RestController
@RequestMapping("/api/user/sign")
@RequiredArgsConstructor
public class SignController {

    private final SignManager signManager;

    @PostMapping
    public Result<SignResultVO> sign() {
        return Result.ok(signManager.sign(UserContext.getUserId()));
    }

    @GetMapping("/status")
    public Result<SignResultVO> status() {
        return Result.ok(signManager.status(UserContext.getUserId()));
    }
}
