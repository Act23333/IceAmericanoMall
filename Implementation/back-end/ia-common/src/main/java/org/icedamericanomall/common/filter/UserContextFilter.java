package org.icedamericanomall.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.icedamericanomall.common.constant.SecurityHeaders;
import org.icedamericanomall.common.security.UserInfo;
import org.icedamericanomall.common.security.UserContext;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 大厂标准: 下游服务 UserContext 重建 + MDC 赋值。
 * <p>
 * 从网关透传的 Headers 中重建 {@link UserInfo}，写入 ThreadLocal + MDC。
 * 与 gateway-service 的 {@code UserContextHeaderFilter} 配对使用。
 * <p>
 * ── 下游 Filter 链 ──
 * {@link TraceIdFilter}(-1000) → UserContextFilter(-990)
 *   → UserContextAuthenticationFilter → Controller
 *
 * <pre>
 * Headers 约定 (网关注入，下游消费):
 *   X-User-Id        — 用户ID
 *   X-Username       — 用户名
 *   X-User-Roles     — 角色 (逗号分隔, "ROLE_USER,ROLE_VIP")
 *   X-User-Permissions — 权限 (逗号分隔, "ai:chat,order:read")
 *   X-Trace-Id       — 全链路追踪ID
 *   X-Client-Ip      — 客户端IP
 *   X-Tenant-Id      — 租户ID
 * </pre>
 *
 * <p><b>为何 {@code clientIp} 不直接复用 {@link org.icedamericanomall.common.utils.IpUtil}</b>：
 * 本字段是<b>审计/日志（MDC）展示口径</b>，不参与限流，因此刻意只认「网关注入的
 * {@code X-Client-Ip} + TCP 对端 {@code remoteAddr} 兜底」，<b>不回落到可伪造的
 * {@code X-Forwarded-For}</b> —— 避免直连场景把客户端自由构造的代理头写进审计链路。
 * {@code IpUtil} 服务于 {@code @RateLimit(key = "ip")} 等安全维度，两者职责不同、回落链不同，
 * 故此处保留独立取值逻辑（单一可读来源：网关 {@code UntrustedHeaderSanitizerFilter} 的覆写）。
 */
@Slf4j
@Component
@Order(UserContextFilter.ORDER)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class UserContextFilter extends OncePerRequestFilter {

    /** Servlet Filter 顺序: 在 TraceIdFilter(HIGHEST_PRECEDENCE) 之后立即执行 */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        String userId = request.getHeader(SecurityHeaders.X_USER_ID);
        if (userId != null && !userId.isBlank()) {
            try {
                String username = request.getHeader(SecurityHeaders.X_USERNAME);
                String traceId = request.getHeader(SecurityHeaders.X_TRACE_ID);
                String clientIp = request.getHeader(SecurityHeaders.X_CLIENT_IP);
                String tenantId = request.getHeader(SecurityHeaders.X_TENANT_ID);

                Set<String> roles = parseCsv(request.getHeader(SecurityHeaders.X_USER_ROLES));
                Set<String> permissions = parseCsv(request.getHeader(SecurityHeaders.X_USER_PERMISSIONS));

                UserInfo userInfo = UserInfo.builder()
                        .userId(Long.valueOf(userId))
                        .username(username != null ? username : "")
                        .roles(roles)
                        .permissions(permissions)
                        .clientIp(clientIp != null ? clientIp : request.getRemoteAddr())
                        .traceId(traceId != null ? traceId : UUID.randomUUID().toString().replace("-", ""))
                        .tenantId(tenantId != null ? tenantId : "default")
                        .build();

                UserContext.setUser(userInfo);

                // MDC 赋值 — 全链路日志追踪
                MDC.put("userId", userId);
                MDC.put("traceId", userInfo.traceId());
                MDC.put("clientIp", userInfo.clientIp());
                MDC.put("tenantId", userInfo.tenantId());
            } catch (NumberFormatException e) {
                log.debug("X-User-Id 格式异常: {}", userId);
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            UserContext.removeUser();
            MDC.remove("userId");
            MDC.remove("traceId");
            MDC.remove("clientIp");
            MDC.remove("tenantId");
        }
    }

    private static Set<String> parseCsv(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) return Collections.emptySet();
        return Arrays.stream(headerValue.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
