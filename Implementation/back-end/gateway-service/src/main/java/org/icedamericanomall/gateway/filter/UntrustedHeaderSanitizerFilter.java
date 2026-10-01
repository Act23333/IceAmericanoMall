package org.icedamericanomall.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 入站可信头剥离过滤器（V5.2 安全加固，order = -1100 —— 过滤器链最早）。
 *
 * <p>解决的问题：网关对"公开路径"（无 JWT）直接放行且不过滤请求头，外部请求可自带伪造的
 * {@code X-User-Id} / {@code X-User-Roles} 等头直通下游 —— 下游 {@code UserContextFilter}
 * 照常把它们重建为用户上下文（越权风险）。可信的用户上下文头<b>只能由网关注入</b>
 * （{@link UserContextHeaderFilter} 在 JWT 校验通过后写入），因此本过滤器在过滤器链入口
 * <b>无条件</b>移除全部入站同名单，随后（若有 JWT）再由网关重新注入。
 *
 * <p>Filter 链顺序：
 * {@code UntrustedHeaderSanitizerFilter(-1100) → TraceIdFilter(-1000) → Security Auth(-800)
 * → UserContextHeaderFilter(-600) → Route}。
 *
 * <p>注意：{@code X-Trace-Id} 不在剥离名单 —— 链路追踪允许上游透传（{@code TraceIdFilter} 语义）。
 */
@Slf4j
@Component
public class UntrustedHeaderSanitizerFilter implements GlobalFilter, Ordered {

    /**
     * 可信用户上下文头（仅允许网关注入，入站一律剥离）：
     * 用户身份 / 角色 / 权限 / 租户 / 客户端 IP（后三者同样只应由网关按真实连接信息注入）。
     */
    public static final List<String> TRUSTED_CONTEXT_HEADERS = List.of(
            "X-User-Id",
            "X-Username",
            "X-User-Roles",
            "X-User-Permissions",
            "X-Tenant-Id",
            "X-Client-Ip");

    /** 过滤器链最早：早于 TraceIdFilter(-1000) 与 Spring Security 认证 */
    private static final int ORDER = -1100;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        List<String> forged = TRUSTED_CONTEXT_HEADERS.stream()
                .filter(header -> exchange.getRequest().getHeaders().containsKey(header))
                .toList();
        if (forged.isEmpty()) {
            return chain.filter(exchange);
        }
        log.warn("[gateway][header-sanitize] 剥离入站伪造上下文头: headers={}, path={}",
                forged, exchange.getRequest().getURI().getPath());

        ServerHttpRequest sanitized = exchange.getRequest().mutate()
                .headers(headers -> TRUSTED_CONTEXT_HEADERS.forEach(headers::remove))
                .build();
        return chain.filter(exchange.mutate().request(sanitized).build());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
