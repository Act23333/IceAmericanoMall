package org.icedamericanomall.user.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.domain.port.SignInStore;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 签到存储适配器 —— Redis Bitmap 实现领域端口 {@link SignInStore}。
 *
 * <p>Key 布局：{@code user:sign:{userId}:{yyyy:MM}}，1 位/天（当月），
 * 首次签到设置一年后过期（原始实现语义，未变更）。
 */
@Component
@RequiredArgsConstructor
public class RedisSignInStoreAdapter implements SignInStore {

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy:MM");

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean markToday(Long userId) {
        LocalDate now = LocalDate.now();
        String key = buildSignKey(userId, now);
        int offset = now.getDayOfMonth() - 1;
        Boolean alreadySigned = stringRedisTemplate.opsForValue().setBit(key, offset, true);
        if (Boolean.FALSE.equals(alreadySigned)) {
            Instant expireInstant = now.plusYears(1)
                    .atStartOfDay()
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
            stringRedisTemplate.expireAt(key, expireInstant);
            return true;
        }
        return false;
    }

    @Override
    public boolean isSignedToday(Long userId) {
        LocalDate now = LocalDate.now();
        String key = buildSignKey(userId, now);
        int offset = now.getDayOfMonth() - 1;
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().getBit(key, offset));
    }

    @Override
    public long countCurrentMonth(Long userId) {
        LocalDate now = LocalDate.now();
        String key = buildSignKey(userId, now);
        // 获取这个月的第几天 - 1 = 签到的终点开始
        int bits = now.getDayOfMonth();
        // 获取 redis 中的签到信息,返回的是一个十进制数字 BITFIELD key get u14 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(bits))
                        .valueAt(0));
        if (CollectionUtils.isEmpty(result)) {
            return 0;
        }
        return Long.bitCount(result.getFirst());
    }

    @Override
    public long countContinuous(Long userId) {
        LocalDate now = LocalDate.now();
        String key = buildSignKey(userId, now);
        int today = now.getDayOfMonth();
        long count = 0;
        for (int i = today - 1; i >= 0; i--) {
            boolean isSigned = Boolean.TRUE.equals(stringRedisTemplate.opsForValue().getBit(key, i));
            if (isSigned) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private String buildSignKey(Long userId, LocalDate date) {
        return String.format("user:sign:%d:%s", userId, date.format(YEAR_MONTH));
    }
}
