package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.UserProfileClient;
import org.icedamericanomall.common.domain.Result;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用户画像（浏览历史 / 收藏）降级工厂 —— <b>展示型查询，fail-soft（返回空列表）</b>。
 *
 * <p>为什么返回空集合而不是抛异常（本包中唯一采用纯 fail-soft 的 Client）：
 * <ul>
 *   <li>唯一调用方 {@code ai-service UserProfileTool}（推荐子 Agent）的语义是
 *       「有画像就多参考一点，没有就照常回答」。空历史 / 空收藏是<b>合法的业务事实</b>
 *       （新注册用户本来就没有数据），与「服务不可用」在业务上等价，不构成错误信息污染。</li>
 *   <li>用户画像属于推荐增强，抛异常会把一次可用的对话变成失败响应，收益为负。</li>
 * </ul>
 *
 * <p>返回 {@link Result#ok(Object)} 而非 {@code Result.error(...)}：此处「空」是成功的结果，
 * 语义上必须与故障区分开，故用成功态承载空列表。
 *
 * <p>⚠️ 生效前提：调用方需开启 {@code spring.cloud.openfeign.circuitbreaker.enabled=true}
 * 且扫描 {@code org.icedamericanomall.fallback}，详见本包 {@code package-info}。
 */
@Slf4j
@Component
public class UserProfileClientFallback implements FallbackFactory<UserProfileClient> {

    @Override
    public UserProfileClient create(Throwable cause) {
        return new UserProfileClient() {

            @Override
            public Result<List<String>> getBrowseHistory(int size) {
                log.warn("用户画像服务降级：浏览历史降级为空列表 size={}", size, cause);
                return Result.ok(List.of());
            }

            @Override
            public Result<List<String>> getFavorites(int page, int size) {
                log.warn("用户画像服务降级：收藏列表降级为空列表 page={}, size={}", page, size, cause);
                return Result.ok(List.of());
            }
        };
    }
}
