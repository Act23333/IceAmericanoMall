package org.icedamericanomall.client;

import org.icedamericanomall.fallback.PointsClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 积分域 Feign Client —— 积分能力归属 marketing-service（ADR-016 收尾，自 user-service 迁出）。
 */
@FeignClient(name = "marketing-service", path = "/internal/points", contextId = "points",
        fallbackFactory = PointsClientFallback.class)
public interface PointsClient {

    @PostMapping("/add")
    Long addPoints(@RequestParam(name = "user_id") Long userId, @RequestParam int points,
                   @RequestParam(defaultValue = "2") int type,
                   @RequestParam(defaultValue = "下单奖励") String source);
}
