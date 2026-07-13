package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.service.UserService;
import org.springframework.web.bind.annotation.*;

/**
 * 余额域内部接口 —— Feign 调用（原本混在 InternalUserController 中）。
 */
@RestController
@RequestMapping("/internal/balance")
@RequiredArgsConstructor
public class InternalBalanceController {

    private final UserService userService;

    @PostMapping("/deduct")
    public void deductBalance(@RequestParam Long userId, @RequestParam Integer amount) {
        userService.deductBalance(userId, amount);
    }
}
