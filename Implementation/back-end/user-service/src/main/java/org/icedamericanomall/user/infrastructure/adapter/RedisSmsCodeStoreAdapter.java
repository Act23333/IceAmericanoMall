package org.icedamericanomall.user.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.domain.port.SmsCodeStore;
import org.icedamericanomall.user.infrastructure.constant.RedisKeyConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 短信验证码读取适配器 —— Redis {@code sms:code:{phone}} 契约。
 *
 * <p>写入方为 verification-service（TTL 5 分钟，ADR-016 发码迁移）；
 * 本地读取仅供 {@code SmsCodeVerifier}（灰度后门路径）使用。
 */
@Component
@RequiredArgsConstructor
public class RedisSmsCodeStoreAdapter implements SmsCodeStore {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public String read(String phone) {
        return stringRedisTemplate.opsForValue().get(RedisKeyConstants.SMS_CODE_PREFIX + phone);
    }

    @Override
    public void remove(String phone) {
        stringRedisTemplate.delete(RedisKeyConstants.SMS_CODE_PREFIX + phone);
    }
}
