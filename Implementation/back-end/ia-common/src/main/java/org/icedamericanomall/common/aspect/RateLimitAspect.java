package org.icedamericanomall.common.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.icedamericanomall.common.annotation.RateLimit;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.IpUtil;
import org.icedamericanomall.common.utils.RateLimitUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.stereotype.Component;
import static org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET;

/**
 * {@code @RateLimit} 注解切面 —— 通过 {@link RateLimitUtils} 做 Redis 计数限流。
 *
 * <p>{@code @ConditionalOnClass} 守卫（V5.2 补齐，R-1 遗留项）：本类强依赖
 * {@link RateLimitUtils}，而后者在类路径无 Redis 时不会被注册。
 * 若不一起守卫，无 Redis 的服务会因「找不到 RateLimitUtils Bean」启动失败
 * （{@code NoSuchBeanDefinitionException}）。两者使用<b>同一个</b>条件表达式，
 * 保证「切面存在 ⟺ 工具存在」，永不出现单边注册。
 */
@Aspect
@Component
@ConditionalOnWebApplication(type = SERVLET)
@ConditionalOnClass(StringRedisTemplate.class)
@Slf4j
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimitUtils rateLimitUtils;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        // 1. Resolve key from SpEL or special "ip" keyword
        String spEL = rateLimit.key();
        String key;
        if ("ip".equals(spEL)) {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                throw new BizException(ErrorCode.ILLEGAL_REQUEST);
            }
            key = IpUtil.getIpAddr(attributes.getRequest());
        } else {
            key = rateLimitUtils.parseKey(spEL, joinPoint);
        }

        String redisKey = "rate:limit:" + key;

        try {
            // checkRateLimit returns true when NOT over limit, false when OVER limit
            boolean notOverLimit = rateLimitUtils.checkRateLimit(
                    redisKey, rateLimit.limit(), rateLimit.unit().toSeconds(rateLimit.duration()));
            if (!notOverLimit) {
                throw new BizException(ErrorCode.FREQUENT_ERROR);
            }
        } catch (DataAccessException e) {
            // Redis unavailable — degrade gracefully, do NOT block the request
            log.error("Rate limit Redis error, key: {}, degrading to pass-through", key, e);
        }

        return joinPoint.proceed();
    }
}
