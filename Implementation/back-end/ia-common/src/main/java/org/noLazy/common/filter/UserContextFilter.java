package org.noLazy.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.noLazy.common.domain.UserInfo;
import org.noLazy.common.utils.UserContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从网关透传的 X-User-Id / X-Username 请求头中提取用户信息，写入 ThreadLocal。
 * 与 gate-service 的 UserContextHeaderFilter 配对使用。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class UserContextFilter extends OncePerRequestFilter {
    private final UserInfoService userInfoService;
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            try {
                UserInfo userInfo = UserInfo.builder()
                        .userId()
                        .username()
                        .roles()
                        .permission()
                        .clientIp()
                        .traceId()
                        .tenantId()
                        .build();
                UserContext.setUser(userInfo);
            } catch (NumberFormatException e) {
                log.debug("X-User-Id 格式异常: {}", userId);
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            UserContext.removeUser();
        }
    }
}
