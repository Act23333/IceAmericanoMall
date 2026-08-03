package org.icedamericanomall.balance.controller;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.balance.application.BalancePayAppService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

/**
 * 余额支付接口 — Interface 层
 *
 * <pre>
 * DDD COLA 调用链:
 *   Controller(参数校验+Result包装) → AppService(@Transactional) → Domain → Infrastructure
 *
 * 职责: 仅参数校验 + 委托 AppService + 封装 Result，不写任何业务逻辑。
 * </pre>
 */
@RestController
@RequestMapping("/api/user/balance")
@RequiredArgsConstructor
public class BalancePayController {

    private final BalancePayAppService balancePayAppService;

    /** 余额支付 */
    @PostMapping("/pay")
    public Result<Integer> pay(@RequestParam int amount) {
        Long userId = UserContext.getUserId();
        int newBalance = balancePayAppService.pay(userId, amount);
        return Result.ok(newBalance);
    }

    /** 余额充值 */
    @PostMapping("/recharge")
    public Result<Integer> recharge(@RequestParam int amount) {
        Long userId = UserContext.getUserId();
        int newBalance = balancePayAppService.recharge(userId, amount);
        return Result.ok(newBalance);
    }

    /** 查询余额（只读，无需事务） */
    @GetMapping
    public Result<Integer> getBalance() {
        Long userId = UserContext.getUserId();
        return Result.ok(balancePayAppService.getBalance(userId));
    }
}
