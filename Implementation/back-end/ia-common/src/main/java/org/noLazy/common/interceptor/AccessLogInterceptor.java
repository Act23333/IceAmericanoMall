package org.noLazy.common.interceptor;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.time.Duration;
import java.time.Instant;

/**
 * @ClassName: AccessLogInterceptor
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/23 16:43
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.noLazy.common.interceptor
 */
@Slf4j
public class AccessLogInterceptor implements HandlerInterceptor {
    private static final String START_TIME_ATTR = AccessLogInterceptor.class.getName() + ".START_TIME";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 记录请求进入时间
        Instant startTime = Instant.now();
        request.setAttribute(START_TIME_ATTR, startTime);
        // 可选：记录请求开始日志（包含用户、URI）
        String userId = "anonymous";
        if (UserContext.getUser() != null) {
            userId = String.valueOf(UserContext.getUser().userId());
        }
        log.info("[请求开始] user={} method={} uri={}", userId, request.getMethod(), request.getRequestURI());
        //为什么没有用户信息，也放行？
        //原因：已经在网关服务中校验了用户权限和密码，并且通过配置判断是否需要拦截该请求，后续只要有用户信息说明就是需要登录权限，存储用户信息，不需要就是不需要登录权限但都要放行，只有两种情况，需不需要用户信息到有没有用户信息，即是否要存储用户信息
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 获取请求进入时间
        Instant startTime = (Instant) request.getAttribute(START_TIME_ATTR);
        if (startTime == null) {
            return; // 防御
        }

        Instant endTime = Instant.now();
        long duration = Duration.between(startTime, endTime).toMillis();

        String userId = "anonymous";
        if (UserContext.getUser() != null) {
            userId = String.valueOf(UserContext.getUser().userId());
        }

        if (ex == null) {
            log.info("[请求结束] user={} uri={} status={} 耗时={}ms",
                    userId, request.getRequestURI(), response.getStatus(), duration);
        } else {
            log.error("[请求异常] user={} uri={} 耗时={}ms 异常={}",
                    userId, request.getRequestURI(), duration, ex.getMessage(), ex);
        }
    }
}
