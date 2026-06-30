package org.noLazy.common.utils;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET;

import java.lang.reflect.Method;
import java.util.Collections;

@Slf4j
@Component
@ConditionalOnWebApplication(type = SERVLET)
public class RateLimitUtils {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    @Resource
    private DefaultRedisScript<Long> rateLimitRedisScript;




    /**
     * 单级限流检查（Redis 计数器）
     * @param redisKey Redis key
     * @param limit 限制次数
     * @param durationSeconds 时间窗口（秒）
     * @return true 表示未超限，false 表示超限
     */
    public boolean checkRateLimit(String redisKey, int limit, long durationSeconds) {
        Long result = stringRedisTemplate.execute(
                rateLimitRedisScript,
                Collections.singletonList(redisKey),
                String.valueOf(limit),
                String.valueOf(durationSeconds)
        );
        if (result != null && result == 1) {
            log.warn("限流触发: key={}, limit={}", redisKey, limit);
            return false;
        }
        return true;
    }

    /**
     * 多级限流检查（用于手机号）
     * @param phone 手机号
     * @return true 表示通过，false 表示超限
     */
    public boolean checkSmsByPhone(String phone) {
        // 分钟限流
        String minuteKey = "sms:minute:" + phone;
        if (!checkRateLimit(minuteKey, 3, 60)) {
            return false;
        }
        // 小时限流
        String hourKey = "sms:hour:" + phone;
        if (!checkRateLimit(hourKey, 10, 3600)) {
            return false;
        }
        // 天限流
        String dayKey = "sms:day:" + phone;
        return checkRateLimit(dayKey, 30, 86400);
    }

    public boolean checkSmsByIp() {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes)RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return false;
        }
        String ip = IpUtil.getIpAddr(requestAttributes.getRequest());
        String redisKey = "sms:ip:" + ip;
        return checkRateLimit(redisKey, 3, 60);
    }

    /**
     * 删除redis键（登录成功后调用）
     */
    public void deleteRedisKeyRedisScript(String redisKey) {
        Boolean result = stringRedisTemplate.delete(redisKey);
        if (result != null && !result) {
            log.warn("删除失败: key={}", redisKey);
        }
    }

    /**
     * 解析 SpEL 表达式，从方法参数中提取值
     */
    public String parseKey(String spel, ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        StandardEvaluationContext context = new StandardEvaluationContext();

        // ✅ 核心修改：不依赖参数名，直接根据下标绑定 p0/p1，永远生效！
        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                context.setVariable("p" + i, args[i]);
            }
        }

        // 如果能拿到参数名，再额外绑定 #phone
        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
        if (paramNames != null && paramNames.length > 0) {
            for (int i = 0; i < paramNames.length; i++) {
                assert args != null;
                context.setVariable(paramNames[i], args[i]);
            }
        }

        return parser.parseExpression(spel).getValue(context, String.class);
    }
}