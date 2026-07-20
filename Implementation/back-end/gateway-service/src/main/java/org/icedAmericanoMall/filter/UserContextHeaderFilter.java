package org.icedAmericanoMall.filter;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 大厂标准: 网关用户上下文过滤器。
 * <p>
 * 职责: JWT提取userId → Redis查权限 → 注入Headers → MDC赋值。
 * 认证(网关) + 授权(微服务) 职责分离 — 网关只验JWT+传身份，不做权限校验。
 *
 * <pre>
 * ── 网关 Filter 链 (Reactive) ──
 * {@link org.icedAmericanoMall.filter.TraceIdFilter}(-1000) → Spring Security Auth
 *   → UserContextHeaderFilter(-600) → {@link AiTokenQuotaFilter}(-50) → Route
 *
 * ── 下游服务 Filter 链 (Servlet) ──
 * {@link org.noLazy.common.filter.TraceIdFilter} → {@link org.noLazy.common.filter.UserContextFilter}
 *   → UserContextAuthenticationFilter → Controller
 * </pre>
 *
 * @see org.icedAmericanoMall.filter.TraceIdFilter 网关 traceId 生成/透传
 * @see org.noLazy.common.filter.UserContextFilter 下游 UserInfo 重建 + MDC
 */
@Slf4j
@Component
public class UserContextHeaderFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -600; // Security过滤器之后, RateLimit之前
    private static final String PERMS_KEY_PREFIX = "user:perms:";
    private static final String DEFAULT_TENANT = "default";

    private final StringRedisTemplate redisTemplate;

    public UserContextHeaderFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .defaultIfEmpty(new SecurityContextImpl())
                .flatMap(ctx -> {
                    Authentication auth = ctx.getAuthentication();
                    if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
                        return chain.filter(exchange); // 公开路径，放行
                    }

                    Long userId = jwt.getClaim("userId");
                    String username = jwt.getClaim("username");

                    // traceId: 透传或生成
                    String traceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
                    if (traceId == null || traceId.isBlank()) {
                        traceId = UUID.randomUUID().toString().replace("-", "");
                    }

                    // clientIp
                    String clientIp = exchange.getRequest().getRemoteAddress() != null
                            ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                            : "unknown";

                    // tenantId
                    String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
                    if (tenantId == null || tenantId.isBlank()) {
                        tenantId = DEFAULT_TENANT;
                    }

                    // MDC (Reactor context — 网关层尽力而为)
                    MDC.put("userId", String.valueOf(userId));
                    MDC.put("traceId", traceId);

                    // Redis 查询权限
                    String permsKey = PERMS_KEY_PREFIX + userId;
                    String finalTraceId = traceId;
                    String finalClientIp = clientIp;
                    String finalTenantId = tenantId;
                    String finalUsername = username != null ? username : "";

                    return Mono.fromCallable(() ->
                                    redisTemplate.opsForSet().members(permsKey))
                            .defaultIfEmpty(Set.of())
                            .flatMap(perms -> enrichHeaders(exchange, chain, userId, finalUsername,
                                    finalTraceId, finalClientIp, finalTenantId, perms));
                });
    }

    /** 从权限集合中提取角色（以 ROLE_ 开头） */
    private String extractRoles(Set<String> permissions) {
        if (permissions == null || permissions.isEmpty()) return "";
        return permissions.stream()
                .filter(p -> p.startsWith("ROLE_"))
                .collect(Collectors.joining(","));
    }

    /** 构建携带用户上下文的请求头并放行 */
    private Mono<Void> enrichHeaders(ServerWebExchange exchange, GatewayFilterChain chain,
                                      Long userId, String username, String traceId,
                                      String clientIp, String tenantId, Set<String> permissions) {
        String rolesStr = extractRoles(permissions);
        String permsStr = String.join(",", permissions != null ? permissions : Set.of());

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", String.valueOf(userId))
                .header("X-Username", username)
                .header("X-User-Roles", rolesStr)
                .header("X-User-Permissions", permsStr)
                .header("X-Trace-Id", traceId)
                .header("X-Client-Ip", clientIp)
                .header("X-Tenant-Id", tenantId)
                .build();

        log.debug("UserContextHeader: userId={}, roles={}, traceId={}", userId, rolesStr, traceId);
        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .doFinally(s -> MDC.clear());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
