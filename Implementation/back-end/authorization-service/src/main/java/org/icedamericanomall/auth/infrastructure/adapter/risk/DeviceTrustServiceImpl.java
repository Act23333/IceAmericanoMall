package org.icedamericanomall.auth.infrastructure.adapter.risk;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.auth.app.port.risk.DeviceTrustService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 设备信任判断实现（基础设施层）—— Redis Set/Key 白名单。
 */
@Component
@RequiredArgsConstructor
public class DeviceTrustServiceImpl implements DeviceTrustService {

    /** Key 前缀：{@code risk:device:trusted:{deviceId}} */
    private static final String TRUSTED_DEVICE_PREFIX = "risk:device:trusted:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean isTrusted(String deviceId) {
        if (deviceId == null) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(TRUSTED_DEVICE_PREFIX + deviceId));
    }
}
