package org.icedamericanomall.client;

import org.icedamericanomall.config.DefaultFeignConfig;
import org.icedamericanomall.fallback.UserProfileClientFallback;
import org.icedamericanomall.common.api.Result;
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
 *
 * <p><b>已知偏差（待迁移，V5.3 审计记录）</b>：本客户端走的是 user-service 的<b>公开契约</b>
 * {@code path = "/api/user"}，而集群内服务间调用应统一走 {@code /internal/**}
 * （见 ADR-004：内部路径不包装 {@code Result}、由 Gateway 拦截外部访问）。
 * 迁移需要 user-service 提供对应的内部端点（{@code /internal/user/history}、
 * {@code /internal/user/favorite}），超出 ia-api 的改动范围，本次仅登记偏差。
 * 迁移完成前，本客户端仍按公开契约解析 {@code Result} 信封。
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
