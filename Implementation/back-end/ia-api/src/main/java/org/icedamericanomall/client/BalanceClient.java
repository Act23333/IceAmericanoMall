package org.icedamericanomall.client;

import org.icedamericanomall.fallback.BalanceClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 余额域 Feign Client —— 从 UserClient 拆分出的 deductBalance。
 */
@FeignClient(name = "user-service", path = "/internal/balance", contextId = "balance",
        fallbackFactory = BalanceClientFallback.class)
public interface BalanceClient {

    @PostMapping("/deduct")
    void deductBalance(@RequestParam Long userId, @RequestParam Integer amount);
}
