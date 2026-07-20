package org.icedAmericanoMall.client;

import org.icedAmericanoMall.config.DefaultFeignConfig;
import org.icedAmericanoMall.dto.UserProfileDTO;
import org.noLazy.common.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * V3.0: Feign client for user-service user profile + history + favorites.
 * Used by ai-service Recommend Subagent.
 */
@FeignClient(
    name = "user-service",
    path = "/api/user",
    configuration = DefaultFeignConfig.class
)
public interface UserProfileClient {

    /**
     * 获取用户浏览历史。
     */
    @GetMapping("/history")
    Result<List<String>> getBrowseHistory(@RequestParam(defaultValue = "20") int size);

    /**
     * 获取用户收藏商品ID列表。
     */
    @GetMapping("/favorite")
    Result<List<String>> getFavorites(@RequestParam(defaultValue = "20") int page,
                                      @RequestParam(defaultValue = "20") int size);
}
