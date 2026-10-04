package org.icedamericanomall.common.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
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
 * <p><b>限流键（V5.3 修正，P1 缺陷）</b>：{@code rate:limit:{类名.方法名}:{业务键}}。
 * 此前键为 {@code rate:limit:{业务键}}，缺少方法维度 —— 同一服务内两个
 * {@code @RateLimit(key = "ip")} 方法（如「发短信」与「提交订单」）会共享同一个计数器桶，
 * 把彼此的次数吃光（一个接口的流量直接导致另一个接口被误限流）。加入方法维度后各方法独立计数。
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

    /** Redis 限流键前缀。 */
    private static final String REDIS_KEY_PREFIX = "rate:limit:";

    /** {@code @RateLimit(key = "ip")} 特殊关键字：以请求来源 IP 为业务键。 */
    private static final String IP_KEYWORD = "ip";

    private final RateLimitUtils rateLimitUtils;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String redisKey = buildRedisKey(joinPoint, rateLimit);

        try {
            // checkRateLimit returns true when NOT over limit, false when OVER limit
            boolean notOverLimit = rateLimitUtils.checkRateLimit(
                    redisKey, rateLimit.limit(), rateLimit.unit().toSeconds(rateLimit.duration()));
            if (!notOverLimit) {
                throw new BizException(ErrorCode.FREQUENT_ERROR);
            }
        } catch (DataAccessException e) {
            // Redis unavailable — degrade gracefully, do NOT block the request
            log.error("Rate limit Redis error, key: {}, degrading to pass-through", redisKey, e);
        }

        return joinPoint.proceed();
    }

    /**
     * 组装 Redis 限流键（包可见，便于单测锁定键格式）。
     *
     * @param joinPoint 被拦截方法（提供「类名.方法名」维度）
     * @param rateLimit 注解配置（提供业务键维度：SpEL 或 {@code ip} 关键字）
     * @return {@code rate:limit:{类名.方法名}:{业务键}}
     * @throws BizException 注解声明 {@code key="ip"} 但当前线程无请求上下文时抛出
     */
    String buildRedisKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        return REDIS_KEY_PREFIX + methodDimension(joinPoint) + ":" + resolveKeyDimension(joinPoint, rateLimit);
    }

    /** 方法维度：{@code 类名.方法名}。 */
    private static String methodDimension(ProceedingJoinPoint joinPoint) {
        Signature signature = joinPoint.getSignature();
        return signature.getDeclaringType().getSimpleName() + "." + signature.getName();
    }

    /** 业务键维度：{@code ip} 关键字取请求 IP，否则按 SpEL 从方法参数取值。 */
    private String resolveKeyDimension(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        String spEL = rateLimit.key();
        if (!IP_KEYWORD.equals(spEL)) {
            return rateLimitUtils.parseKey(spEL, joinPoint);
        }
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new BizException(ErrorCode.ILLEGAL_REQUEST);
        }
        return IpUtil.getIpAddr(attributes.getRequest());
    }
}
