package org.icedamericanomall.client;

import org.icedamericanomall.fallback.UserClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 纯用户域 Feign Client（V2.5 拆分：auth→AuthClient, points→PointsClient, balance→BalanceClient）。
 */
@FeignClient(name = "user-service", path = "/internal/user", contextId = "internal", fallbackFactory = UserClientFallback.class)
public interface UserClient {

    @GetMapping("/count")
    Long countUsers();
}
