package org.noLazy.common.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.noLazy.common.annotation.RateLimit;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.noLazy.common.utils.IpUtil;
import org.noLazy.common.utils.RateLimitUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DataAccessException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.springframework.stereotype.Component;

import static org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET;

@Aspect
@Component
@ConditionalOnWebApplication(type = SERVLET)
@Slf4j
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimitUtils rateLimitUtils;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        // 1. Resolve key from SpEL or special "ip" keyword
        String spel = rateLimit.key();
        String key;
        if ("ip".equals(spel)) {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                throw new BizException(ErrorCode.ILLEGAL_REQUEST);
            }
            key = IpUtil.getIpAddr(attributes.getRequest());
        } else {
            key = rateLimitUtils.parseKey(spel, joinPoint);
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
