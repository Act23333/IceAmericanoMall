package org.icedamericanomall.client;

import org.icedamericanomall.fallback.PointsClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 积分域 Feign Client —— 从 UserClient 拆分出的 addPoints。
 */
@FeignClient(name = "user-service", path = "/internal/points", contextId = "points",
        fallbackFactory = PointsClientFallback.class)
public interface PointsClient {

    @PostMapping("/add")
    Long addPoints(@RequestParam Long userId, @RequestParam int points,
                   @RequestParam(defaultValue = "2") int type,
                   @RequestParam(defaultValue = "下单奖励") String source);
}
