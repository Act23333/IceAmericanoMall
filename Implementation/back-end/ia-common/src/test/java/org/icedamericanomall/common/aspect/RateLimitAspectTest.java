package org.icedamericanomall.common.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.icedamericanomall.common.annotation.RateLimit;
import org.icedamericanomall.common.utils.RateLimitUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code @RateLimit} 键格式回归测试（P1）。
 *
 * <p>锁死不变量：限流键必须带「类名.方法名」维度，否则同服务内两个
 * {@code key="ip"} 的方法会共用一个计数器桶（一个接口的流量把另一个接口打爆）。
 */
@DisplayName("RateLimitAspect — 限流键含方法维度")
class RateLimitAspectTest {

    private static final String CLIENT_IP = "203.0.113.7";

    private final RateLimitUtils rateLimitUtils = mock(RateLimitUtils.class);

    private final RateLimitAspect aspect = new RateLimitAspect(rateLimitUtils);

    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("key=ip → 键为 rate:limit:{类名.方法名}:{IP}，并把配置的次数/窗口透传给工具")
    void shouldBuildMethodScopedKey_whenIpKeyword() throws Throwable {
        bindRequestWithRemoteAddr(CLIENT_IP);
        when(rateLimitUtils.checkRateLimit(anyString(), anyInt(), anyLong())).thenReturn(true);
        ProceedingJoinPoint joinPoint = mockJoinPoint(SmsController.class, "send");
        RateLimit rateLimit = mockRateLimit("ip", 5, 60, TimeUnit.SECONDS);

        aspect.around(joinPoint, rateLimit);

        ArgumentCaptor<String> redisKey = ArgumentCaptor.forClass(String.class);
        verify(rateLimitUtils).checkRateLimit(redisKey.capture(), eq(5), eq(60L));
        assertThat(redisKey.getValue()).isEqualTo("rate:limit:SmsController.send:" + CLIENT_IP);
    }

    @Test
    @DisplayName("同 IP 的两个方法 → 键互不相同（回归：此前共享一个桶）")
    void shouldNotShareBucket_whenTwoMethodsOfSameClassUseIpKey() {
        bindRequestWithRemoteAddr(CLIENT_IP);
        RateLimit rateLimit = mockRateLimit("ip", 5, 60, TimeUnit.SECONDS);
        ProceedingJoinPoint first = mockJoinPoint(SmsController.class, "send");
        ProceedingJoinPoint second = mockJoinPoint(SmsController.class, "verify");

        String firstKey = aspect.buildRedisKey(first, rateLimit);
        String secondKey = aspect.buildRedisKey(second, rateLimit);

        assertThat(firstKey).isNotEqualTo(secondKey);
        assertThat(firstKey).startsWith("rate:limit:SmsController.send:");
        assertThat(secondKey).startsWith("rate:limit:SmsController.verify:");
    }

    @Test
    @DisplayName("key=SpEL → 键使用 SpEL 解析结果作为业务键")
    void shouldBuildSpelKey_whenKeyIsSpel() {
        when(rateLimitUtils.parseKey(eq("p0.userId"), org.mockito.ArgumentMatchers.any()))
                .thenReturn("42");
        ProceedingJoinPoint joinPoint = mockJoinPoint(OrderController.class, "submit");
        RateLimit rateLimit = mockRateLimit("p0.userId", 10, 1, TimeUnit.MINUTES);

        assertThat(aspect.buildRedisKey(joinPoint, rateLimit))
                .isEqualTo("rate:limit:OrderController.submit:42");
    }

    /** 绑定一次带 remoteAddr 的模拟请求（IpUtil 的兜底取值来源）。 */
    private void bindRequestWithRemoteAddr(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/sms/send");
        request.setRemoteAddr(remoteAddr);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private RateLimit mockRateLimit(String key, int limit, long duration, TimeUnit unit) {
        RateLimit rateLimit = mock(RateLimit.class);
        when(rateLimit.key()).thenReturn(key);
        when(rateLimit.limit()).thenReturn(limit);
        when(rateLimit.duration()).thenReturn(duration);
        when(rateLimit.unit()).thenReturn(unit);
        return rateLimit;
    }

    private ProceedingJoinPoint mockJoinPoint(Class<?> declaringType, String methodName) {
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getDeclaringType()).thenReturn(declaringType);
        when(signature.getName()).thenReturn(methodName);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        return joinPoint;
    }

    /** 被拦截方法所在类的替身（仅用于取 {@code getSimpleName()}）。 */
    static class SmsController {
    }

    /** 被拦截方法所在类的替身（仅用于取 {@code getSimpleName()}）。 */
    static class OrderController {
    }
}
