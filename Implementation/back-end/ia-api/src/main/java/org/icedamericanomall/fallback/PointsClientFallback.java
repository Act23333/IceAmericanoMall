package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.PointsClient;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PointsClientFallback implements FallbackFactory<PointsClient> {
    @Override
    public PointsClient create(Throwable cause) {
        return (userId, points, type, source) -> {
            log.error("积分发放失败: userId={}, points={}", userId, points, cause);
            return 0L;
        };
    }
}
