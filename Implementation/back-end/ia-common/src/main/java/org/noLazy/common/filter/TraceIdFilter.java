package org.noLazy.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

import static org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET;

/**
 * TraceId 过滤器 —— 为每个请求生成/透传 traceId 写入 MDC，供日志 {@code %X{traceId}} 输出与 ELK 关联。
 * 上游（网关）可通过 {@code X-Trace-Id} 头透传，否则本地生成。仅 Servlet 应用生效（网关为 Reactive，走 SkyWalking Agent 追踪）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication(type = SERVLET)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    private static final String HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = request.getHeader(HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(TRACE_ID, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID);
        }
    }
}
