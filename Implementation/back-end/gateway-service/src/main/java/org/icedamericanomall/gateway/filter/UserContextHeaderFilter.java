package org.icedamericanomall.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.constant.SecurityHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 大厂标准: 网关用户上下文过滤器。
 *
 * <p>职责: JWT 提取 userId → 非阻塞查 Redis 权限 → 注入请求头。
 * 认证（网关）+ 授权（微服务）职责分离 —— 网关只验 JWT + 传身份，不做权限校验。
 *
 * <p>实现要点：
 * <ul>
 *   <li>权限查询使用 {@link ReactiveStringRedisTemplate}（非阻塞）。禁止在事件循环线程上使用
 *       阻塞式 {@code StringRedisTemplate}，否则 Redis 抖动会阻塞 Netty I/O 线程。</li>
 *   <li>不再读写 MDC：WebFlux 下 MDC 基于 ThreadLocal，事件循环线程会复用于其他请求，
 *       {@code MDC.put} 存在串号风险；链路追踪由 {@code X-Trace-Id} 请求头透传，日志上下文
 *       按 Reactor Context 方案后续演进。</li>
 *   <li>不再注入 {@code X-Client-Ip}：真实客户端 IP 统一由
 *       {@link UntrustedHeaderSanitizerFilter} 在链路入口按 TCP 对端地址写入。
 *       租户头 {@code X-Tenant-Id} 亦不再注入（入站已被剥离，下游 {@code UserContextFilter}
 *       缺省为 {@code default}；多租户传播需求待确认后另行设计）。</li>
 *   <li>{@link JwtBlacklistFilter} 已在上游完成吊销校验；{@code userId} 缺失的 token 不注入
 *       身份头（避免下游解析 {@code "null"} 报错）。</li>
 * </ul>
 *
 * <pre>
 * ── 网关 Filter 链 (Reactive) ──
 * {@link TraceIdFilter}(-1000) → Spring Security Auth → {@link JwtBlacklistFilter}(-700)
 *   → UserContextHeaderFilter(-600) → {@link AiTokenQuotaFilter}(-50) → Route
 *
 * ── 下游服务 Filter 链 (Servlet) ──
 * {@link org.icedamericanomall.common.filter.TraceIdFilter}
 *   → {@link org.icedamericanomall.common.filter.UserContextFilter} → Controller
 * </pre>
 *
 * @see TraceIdFilter 网关 traceId 生成/透传
 * @see org.icedamericanomall.common.filter.UserContextFilter 下游 UserInfo 重建
 */
@Slf4j
@Component
public class UserContextHeaderFilter implements GlobalFilter, Ordered {

    /** Security 过滤器之后, 限流之前 */
    private static final int ORDER = -600;

    /** 权限集合缓存键前缀（由 authorization-service 写入 / 失效）。 */
    private static final String PERMS_KEY_PREFIX = "user:perms:";

    /** JWT 中的用户 ID claim。 */
    private static final String CLAIM_USER_ID = "userId";

    /** JWT 中的用户名 claim（当前签发端未写入，缺失时注入空串）。 */
    private static final String CLAIM_USERNAME = "username";

    private static final String HEADER_USER_ID = SecurityHeaders.X_USER_ID;
    private static final String HEADER_USERNAME = SecurityHeaders.X_USERNAME;
    private static final String HEADER_USER_ROLES = SecurityHeaders.X_USER_ROLES;
    private static final String HEADER_USER_PERMISSIONS = SecurityHeaders.X_USER_PERMISSIONS;

    private final ReactiveStringRedisTemplate redisTemplate;

    public UserContextHeaderFilter(ReactiveStringRedisTemplate redisTemplate) {
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

                    Long userId = jwt.getClaim(CLAIM_USER_ID);
                    if (userId == null) {
                        log.warn("[gateway][user-context] token 缺少 userId claim，不注入身份头: sub={}",
                                jwt.getSubject());
                        return chain.filter(exchange);
                    }
                    String username = jwt.getClaim(CLAIM_USERNAME);

                    // 非阻塞查询权限缓存（authorization-service 维护）
                    return redisTemplate.opsForSet().members(PERMS_KEY_PREFIX + userId)
                            .collectList()
                            .flatMap(perms -> enrichHeaders(exchange, chain, userId, username, Set.copyOf(perms)));
                });
    }

    /** 从权限集合中提取角色（以 ROLE_ 开头） */
    private String extractRoles(Collection<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return "";
        }
        return permissions.stream()
                .filter(p -> p.startsWith("ROLE_"))
                .collect(Collectors.joining(","));
    }

    /** 构建携带用户上下文的请求头并放行 */
    private Mono<Void> enrichHeaders(ServerWebExchange exchange, GatewayFilterChain chain,
                                     Long userId, String username, Set<String> permissions) {
        String rolesStr = extractRoles(permissions);
        String permsStr = String.join(",", permissions);

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(HEADER_USER_ID, String.valueOf(userId))
                .header(HEADER_USERNAME, username != null ? username : "")
                .header(HEADER_USER_ROLES, rolesStr)
                .header(HEADER_USER_PERMISSIONS, permsStr)
                .build();

        log.debug("[gateway][user-context] 注入用户上下文: userId={}, roles={}", userId, rolesStr);
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
