package org.icedamericanomall.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.constant.SecurityHeaders;
import org.icedamericanomall.gateway.util.EdgePeerIpResolver;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * 入站可信头剥离 + 客户端 IP 覆写过滤器（安全加固，order = -1100 —— 过滤器链最早）。
 *
 * <p>解决的问题一：网关对"公开路径"（无 JWT）放行时，外部请求可自带伪造的
 * {@code X-User-Id} / {@code X-User-Roles} 等头直通下游 —— 下游 {@code UserContextFilter}
 * 照常把它们重建为用户上下文（越权风险）。可信的用户上下文头<b>只能由网关注入</b>，
 * 因此本过滤器在链路入口<b>无条件</b>移除全部入站同名单。
 *
 * <p>解决的问题二：伪造客户端 IP。Spring Cloud Gateway 会把 TCP 对端地址<b>追加</b>到
 * 客户端自带的 {@code X-Forwarded-For} 尾部，而下游 {@code IpUtil} 取 XFF 首个有效段
 * （即伪造段）→ 可伪造 IP 打穿短信 / AI 等按 IP 维度的限流。故本过滤器剥离
 * {@code X-Forwarded-For} / {@code X-Real-IP} / {@code Forwarded} 等可伪造代理头
 * （SCG 出站时会按真实连接重新生成 X-Forwarded-For），并把 {@code X-Client-Ip}
 * <b>覆写为真实对端地址</b>（口径由 {@link EdgePeerIpResolver} 提供：只认 TCP 对端，
 * 绝不读入站代理头），下游据此取值即可。
 *
 * <p>Filter 链顺序：
 * {@code UntrustedHeaderSanitizerFilter(-1100) → TraceIdFilter(-1000) → Security Auth(-800)
 * → JwtBlacklistFilter(-700) → UserContextHeaderFilter(-600) → Route}。
 *
 * <p>注意：{@code X-Trace-Id} 不在剥离名单 —— 链路追踪允许上游透传（{@code TraceIdFilter} 语义）。
 */
@Slf4j
@Component
public class UntrustedHeaderSanitizerFilter implements GlobalFilter, Ordered {

    /**
     * 可信用户上下文头（仅允许网关注入，入站一律剥离）：
     * 用户身份 / 角色 / 权限 / 客户端 IP（后者同样只应由网关按真实连接信息注入）。
     */
    public static final List<String> TRUSTED_CONTEXT_HEADERS = List.of(
            SecurityHeaders.X_USER_ID,
            SecurityHeaders.X_USERNAME,
            SecurityHeaders.X_USER_ROLES,
            SecurityHeaders.X_USER_PERMISSIONS,
            SecurityHeaders.X_TENANT_ID,
            SecurityHeaders.X_CLIENT_IP);

    /**
     * 可伪造的客户端真实 IP 代理头（入站一律剥离，出站由 SCG 按真实连接重新生成）。
     * {@code Forwarded} 为 RFC 7239 等价头，一并剥离以堵住旁路口径。
     */
    public static final List<String> SPOOFABLE_IP_HEADERS = List.of(
            SecurityHeaders.X_FORWARDED_FOR,
            SecurityHeaders.X_REAL_IP,
            SecurityHeaders.FORWARDED);

    /** 网关注入给下游的客户端 IP 头（值为真实 TCP 对端地址）。 */
    public static final String CLIENT_IP_HEADER = SecurityHeaders.X_CLIENT_IP;

    /** 入站需清洗的全部请求头（可信上下文头 + 可伪造 IP 代理头）。 */
    private static final List<String> SANITIZED_INBOUND_HEADERS = buildSanitizedHeaders();

    /** 过滤器链最早：早于 TraceIdFilter(-1000) 与 Spring Security 认证 */
    private static final int ORDER = -1100;

    private static List<String> buildSanitizedHeaders() {
        List<String> headers = new ArrayList<>(TRUSTED_CONTEXT_HEADERS);
        headers.addAll(SPOOFABLE_IP_HEADERS);
        return List.copyOf(headers);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        List<String> forged = SANITIZED_INBOUND_HEADERS.stream()
                .filter(header -> exchange.getRequest().getHeaders().containsKey(header))
                .toList();
        if (!forged.isEmpty()) {
            log.warn("[gateway][header-sanitize] 剥离入站伪造上下文/IP 头: headers={}, path={}",
                    forged, exchange.getRequest().getURI().getPath());
        }

        String clientIp = EdgePeerIpResolver.resolve(exchange);
        ServerHttpRequest sanitized = exchange.getRequest().mutate()
                .headers(headers -> {
                    SANITIZED_INBOUND_HEADERS.forEach(headers::remove);
                    // 覆写为真实对端地址：下游只信任网关注入的 X-Client-Ip
                    headers.set(CLIENT_IP_HEADER, clientIp);
                })
                .build();
        return chain.filter(exchange.mutate().request(sanitized).build());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
