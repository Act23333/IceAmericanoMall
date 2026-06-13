package org.icedAmericanoMall.service.impl;

import jakarta.annotation.Resource;
import org.icedAmericanoMall.service.UserSignService;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * @ClassName: UserSignServiceImpl
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/4/24 1:58
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedAmericanoMall.service.impl
 */
@Service
public class UserSignServiceImpl implements UserSignService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy:MM");

    @Override
    public boolean sign(Long userId) {
        LocalDate now = LocalDate.now();
        String key = buildSignKey(userId, now);
        int offset = now.getDayOfMonth() - 1;
        Boolean isSigned = stringRedisTemplate.opsForValue().setBit(key, offset, true);
        if (Boolean.FALSE.equals(isSigned)) {
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
    public boolean isTodaySigned(Long userId) {
        LocalDate now = LocalDate.now();
        String key = buildSignKey(userId, now);
        int offset = now.getDayOfMonth() - 1;
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().getBit(key, offset));
    }

    @Override
    public long countCurrentMonthSign(Long userId) {
        LocalDate now = LocalDate.now();
        String key = buildSignKey(userId, now);
        //获取这个月的第几天 - 1 = 签到的终点开始
        int bits = now.getDayOfMonth();
        //获取redis中的签到信息,返回的是一个十进制数字 BITFIELD key get u14 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.
                        create().
                        get(BitFieldSubCommands.BitFieldType.unsigned(bits)).
                        valueAt(0));
        if (CollectionUtils.isEmpty(result)) {
            return 0;
        }
        return Long.bitCount(result.getFirst());
    }

    @Override
    public long countContinuousSign(Long userId) {
        LocalDate now = LocalDate.now();
        String key = buildSignKey(userId, now);
        int today = now.getDayOfMonth();
        long count = 0;
        for (int i = today - 1; i >= 0; i--) {
            boolean isSigned = Boolean.TRUE.equals(stringRedisTemplate.opsForValue().getBit(key, i));
            if (isSigned) count++;
            else break;
        }
        return count;
    }

    private String buildSignKey(Long userId, LocalDate date) {
        return String.format("user:sign:%d:%s", userId, date.format(YEAR_MONTH));
    }
}