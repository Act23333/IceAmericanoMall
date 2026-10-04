package org.icedamericanomall.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 缓存 POST/PUT/PATCH 请求体（最高优先级，先于 AdaptCachedBodyGlobalFilter 执行），
 * 修复 WebFlux 安全链 / CORS 预检处理时偶发的 body 丢失问题。
 *
 * <p>安全与资源约束：
 * <ul>
 *   <li>缓冲上限 {@code gateway.body-cache.max-bytes}（默认 2MB），超限返回 413，
 *       避免大请求体把网关堆内存打爆（原实现 {@code DataBufferUtils.join(Publisher)} 无上限）。</li>
 *   <li>{@code gateway.body-cache.enabled} 可整体关闭：当前网关过滤器链中没有任何
 *       {@code ModifyRequestBody} / 重放 body 的下游需要，本过滤器是"body 丢失"缺陷的
 *       兜底（保持默认开启）；若确认下游无需缓存可关闭以省一次内存拷贝。</li>
 * </ul>
 *
 * <p>实现约束：读取阶段与下游分发严格分离 —— 缓冲超限只发生在读取阶段，
 * `chain.filter()` 不在任何 {@code onErrorResume} 覆盖范围内，确保每次请求恰好分发一次。
 */
@Slf4j
@Component
public class CacheBodyGlobalFilter implements GlobalFilter, Ordered {

    /** 413 响应体模板（统一错误码 JSON）。 */
    private static final String OVERSIZE_BODY_TEMPLATE = "{\"code\":%d,\"message\":\"%s\"}";

    /** 请求体超限提示语。 */
    private static final String OVERSIZE_MESSAGE = "请求体过大";

    private static final byte[] EMPTY_BYTES = new byte[0];

    private final boolean enabled;
    private final int maxBytes;

    public CacheBodyGlobalFilter(@Value("${gateway.body-cache.enabled:true}") boolean enabled,
                                 @Value("${gateway.body-cache.max-bytes:2097152}") int maxBytes) {
        this.enabled = enabled;
        this.maxBytes = maxBytes;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        if (!enabled || !isBodyCachingMethod(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }

        return readBody(exchange)
                .flatMap(result -> result.tooLarge()
                        ? payloadTooLarge(exchange)
                        : chain.filter(exchange.mutate().request(cachedRequest(exchange, result.bytes())).build()));
    }

    /** 仅对可能带请求体的方法缓存 body。 */
    private boolean isBodyCachingMethod(HttpMethod method) {
        return HttpMethod.POST.equals(method) || HttpMethod.PUT.equals(method) || HttpMethod.PATCH.equals(method);
    }

    /** 读取并释放请求体（带上限）；超限不抛异常，转为结果标记。 */
    private Mono<BodyReadResult> readBody(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody(), maxBytes)
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return BodyReadResult.of(bytes);
                })
                .defaultIfEmpty(BodyReadResult.of(EMPTY_BYTES))
                .onErrorResume(DataBufferLimitException.class, e -> {
                    log.warn("[gateway][body-cache] 请求体超过缓冲上限: path={}, limit={}B",
                            exchange.getRequest().getURI().getPath(), maxBytes);
                    return Mono.just(BodyReadResult.oversize());
                });
    }

    /** 用已缓存的字节重建可重复读取的请求体。 */
    private ServerHttpRequest cachedRequest(ServerWebExchange exchange, byte[] bytes) {
        return new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public Flux<DataBuffer> getBody() {
                return Flux.just(exchange.getResponse().bufferFactory().wrap(bytes));
            }
        };
    }

    /** 输出 413 统一错误体，并终止请求。 */
    private Mono<Void> payloadTooLarge(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.PAYLOAD_TOO_LARGE);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(OVERSIZE_BODY_TEMPLATE, ErrorCode.PARAM_ERROR.getCode(), OVERSIZE_MESSAGE);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE; // 在所有 filter 之前
    }

    /** 请求体读取结果：是否超限 + 已读取的字节。 */
    private record BodyReadResult(boolean tooLarge, byte[] bytes) {

        static BodyReadResult of(byte[] bytes) {
            return new BodyReadResult(false, bytes);
        }

        static BodyReadResult oversize() {
            return new BodyReadResult(true, EMPTY_BYTES);
        }
    }
}
