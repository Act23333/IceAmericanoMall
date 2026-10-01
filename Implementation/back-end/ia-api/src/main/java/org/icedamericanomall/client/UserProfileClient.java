package org.icedamericanomall.client;

import org.icedamericanomall.config.DefaultFeignConfig;
import org.icedamericanomall.fallback.UserProfileClientFallback;
import org.icedamericanomall.common.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * V3.0: Feign client for user-service user profile + history + favorites.
 * Used by ai-service Recommend Subagent.
 *
 * <p>降级语义：<b>fail-soft</b>（返回 {@code Result.ok(空列表)}）。
 * 空历史 / 空收藏是合法业务事实（新用户本来就没有数据），
 * 与「服务不可用」在推荐场景下等价，不构成错误信息污染。
 * 详见 {@link UserProfileClientFallback}。
 */
@FeignClient(
    name = "user-service",
    path = "/api/user",
    contextId = "userProfile",
    configuration = DefaultFeignConfig.class,
    fallbackFactory = UserProfileClientFallback.class
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
