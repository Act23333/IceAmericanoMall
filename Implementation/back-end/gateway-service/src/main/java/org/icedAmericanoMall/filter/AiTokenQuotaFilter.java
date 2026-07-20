package org.icedAmericanoMall.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * V3.0.1: AI Token 配额全局过滤器。
 * <p>
 * 对 `/api/ai/**` 请求进行每日 Token 配额检查。
 * 配额用尽返回 HTTP 429 + 降级提示。
 * <p>
 * 大厂对标: Kong AI Gateway Token-based throttling。
 */
@Slf4j
@Component
public class AiTokenQuotaFilter implements GlobalFilter, Ordered {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private static final String QUOTA_KEY = "ai:quota:";
    private static final int DAILY_LIMIT = 50000;
    private static final int ORDER = -50; // 在 UserContextHeaderFilter (-100) 之后

    public AiTokenQuotaFilter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!path.startsWith("/api/ai/")) {
            return chain.filter(exchange);
        }

        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (userId == null || userId.isBlank()) {
            return chain.filter(exchange);
        }

        String key = QUOTA_KEY + userId + ":" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        return redisTemplate.opsForValue().get(key)
                .flatMap(value -> {
                    long used = value != null ? Long.parseLong(value) : 0;
                    if (used >= DAILY_LIMIT) {
                        log.info("AI token quota exceeded for userId={}", userId);
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        exchange.getResponse().getHeaders().set("Content-Type", "application/json");
                        String body = "{\"code\":3103,\"message\":\"AI 助手今日使用次数已达上限，请明天再来\"}";
                        return exchange.getResponse()
                                .writeWith(Mono.just(exchange.getResponse()
                                        .bufferFactory().wrap(body.getBytes())));
                    }
                    return chain.filter(exchange).then(recordUsage(key, exchange));
                })
                .switchIfEmpty(chain.filter(exchange))
                .onErrorResume(e -> {
                    log.warn("Token quota check failed, allowing: {}", e.getMessage());
                    return chain.filter(exchange);
                });
    }

    private Mono<Void> recordUsage(String key, ServerWebExchange exchange) {
        String contentLength = exchange.getRequest().getHeaders().getFirst("Content-Length");
        int estimatedTokens = contentLength != null ? Integer.parseInt(contentLength) * 2 : 100;
        return redisTemplate.opsForValue().increment(key, estimatedTokens)
                .flatMap(v -> redisTemplate.expire(key, Duration.ofDays(1)))
                .then();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
