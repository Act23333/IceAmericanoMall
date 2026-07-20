package org.icedAmericanoMall.filter;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
 * <p>
 * 职责: 透传或生成 traceId → 注入请求头 X-Trace-Id → 写入 MDC。
 * 下游服务通过 {@link org.noLazy.common.filter.TraceIdFilter} 从 X-Trace-Id 头重建 traceId。
 * <p>
 * Filter 链顺序: TraceIdFilter(-1000) → Auth(-800) → UserContextHeaderFilter(-600)
 */
@Slf4j
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    public static final String TRACE_HEADER = "X-Trace-Id";
    private static final int ORDER = -1000;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        MDC.put("traceId", traceId);

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(TRACE_HEADER, traceId)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .doFinally(s -> MDC.clear());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
