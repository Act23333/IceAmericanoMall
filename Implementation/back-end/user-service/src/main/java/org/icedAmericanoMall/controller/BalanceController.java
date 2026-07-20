package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.service.UserService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

/**
 * 账户余额 —— 查询 + 简易充值（演示/Mock，真实充值应经支付渠道）。
 *
 * <pre>
 * Scenario: 查询余额
 *   Given 用户已登录
 *   When GET /user/balance
 *   Then 返回账户余额（分）
 *
 * Scenario: 简易充值
 *   Given 用户已登录
 *   When POST /user/balance/recharge?amount=10000
 *   Then 余额增加，返回最新余额
 * </pre>
 */
@RestController
@RequestMapping("/api/user/balance")
@RequiredArgsConstructor
public class BalanceController {

    private final UserService userService;

    @GetMapping
    public Result<Integer> getBalance() {
        return Result.ok(userService.getBalance(UserContext.getUserId()));
    }

    /** 简易充值（Mock）：直接为当前用户增加余额，返回最新余额。 */
    @PostMapping("/recharge")
    public Result<Integer> recharge(@RequestParam int amount) {
        Long userId = UserContext.getUserId();
        userService.addBalance(userId, amount);
        return Result.ok(userService.getBalance(userId));
    }
}
