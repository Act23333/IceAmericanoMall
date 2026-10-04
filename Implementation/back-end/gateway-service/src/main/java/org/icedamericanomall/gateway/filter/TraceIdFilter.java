package org.icedamericanomall.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.constant.SecurityHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 大厂标准: 网关 TraceId 全局过滤器 (order = -1000)。
 *
 * <p>职责: 透传或生成 traceId → 注入请求头 X-Trace-Id。
 * 下游服务通过 {@link org.icedamericanomall.common.filter.TraceIdFilter} 从 X-Trace-Id 头重建 traceId。
 *
 * <p>说明：本过滤器<b>不再写 MDC</b>。WebFlux 下 MDC 依赖 ThreadLocal，而事件循环线程被
 * 多个请求复用，{@code MDC.put}/{@code MDC.clear} 会造成日志串号；日志上下文如需绑定，
 * 应改用 Reactor Context / 日志框架的上下文传播方案（后续演进项）。
 *
 * <p>Filter 链顺序: TraceIdFilter(-1000) → Security Auth → JwtBlacklistFilter(-700)
 * → UserContextHeaderFilter(-600) → AiTokenQuotaFilter(-50) → Route
 */
@Slf4j
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    public static final String TRACE_HEADER = SecurityHeaders.X_TRACE_ID;
    private static final int ORDER = -1000;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(TRACE_HEADER, traceId)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
