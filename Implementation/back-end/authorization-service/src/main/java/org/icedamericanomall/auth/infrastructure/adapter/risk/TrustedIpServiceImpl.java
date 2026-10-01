package org.icedamericanomall.auth.infrastructure.adapter.risk;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.auth.app.port.risk.TrustedIpService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * IP 黑白名单实现（基础设施层）—— Redis Set。
 */
@Component
@RequiredArgsConstructor
public class TrustedIpServiceImpl implements TrustedIpService {

    /** 黑名单 Set Key */
    private static final String BLACKLIST_KEY = "risk:ip:blacklist";

    /** 白名单 Set Key */
    private static final String TRUSTED_KEY = "risk:ip:trusted";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean isBlacklisted(String clientIp) {
        return clientIp != null
                && Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(BLACKLIST_KEY, clientIp));
    }

    @Override
    public boolean isTrusted(String clientIp) {
        return clientIp != null
                && Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(TRUSTED_KEY, clientIp));
    }
}
