package org.icedamericanomall.gateway.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

public class JwtBlacklistValidator implements OAuth2TokenValidator<Jwt> {

    private final ReactiveStringRedisTemplate redisTemplate;
    private static final String ACCESS_TOKEN_PREFIX = "auth:blacklist:";

    public JwtBlacklistValidator(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        // 1. 从 JWT 的 Claims 中获取唯一标识 jti
        String jti = jwt.getId(); 
        if (jti == null) {
            // 如果 JWT 中没有 jti，无法进行黑名单校验，可根据安全策略决定是否放行或报错
            return OAuth2TokenValidatorResult.success();
        }

        String blacklistKey = ACCESS_TOKEN_PREFIX + jti;

        // 2. 使用 Reactive Redis 客户端进行非阻塞查询
        return redisTemplate.hasKey(blacklistKey)
                .flatMap(exists -> {
                    if (exists) {
                        // 3. 如果存在，说明令牌已被加入黑名单，验证失败
                        OAuth2Error error = new OAuth2Error(
                                "invalid_token",
                                "The token has been revoked (blacklisted).",
                                null
                        );
                        return Mono.just(OAuth2TokenValidatorResult.failure(error));
                    } else {
                        // 4. 如果不存在，验证通过
                        return Mono.just(OAuth2TokenValidatorResult.success());
                    }
                })
                .block(); // 注意：仅在必要时阻塞，确保 reactive 流在 Security 框架中正确同步
    }
}