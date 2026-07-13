package org.icedAmericanoMall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.client.UserClient;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 纯用户域 Fallback（V2.5 拆分：auth/points/balance 已迁至各自 Client）。
 */
@Slf4j
@Component
public class UserClientFallback implements FallbackFactory<UserClient> {
    @Override
    public UserClient create(Throwable cause) {
        return new UserClient() {
            @Override
            public Long countUsers() {
                log.error("获取用户总数失败", cause);
                return 0L;
            }
        };
    }
}
