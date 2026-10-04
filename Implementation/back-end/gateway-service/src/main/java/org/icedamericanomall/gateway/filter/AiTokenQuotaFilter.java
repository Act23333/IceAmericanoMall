package org.icedamericanomall.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.constant.SecurityHeaders;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.gateway.util.EdgePeerIpResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * AI 导购（{@code /api/ai/**}）每日额度过滤器。
 *
 * <p><b>计数口径</b>：先原子 {@code INCR} 再判定（Redis INCR 天然 upsert），
 * 首次计数时补挂 TTL 到次日零点；超过阈值返回 429。被拒绝的请求同样计入用量，
 * 避免拒绝路径被无限重试放大（口径为"请求次数×估算 Token"，非真实 Token 计量）。
 *
 * <p><b>身份维度</b>：有 {@code X-User-Id}（JWT 已认证）按用户维度计数；
 * 无身份（{@code /api/ai/**} 允许匿名访问）按客户端 IP 维度计数 —— 早期实现直接放行匿名流量，
 * 等于给匿名调用留了无限额度的旁路。IP 取真实 TCP 对端地址（见 {@link EdgePeerIpResolver}），
 * 不使用可伪造的 XFF。
 *
 * <p><b>可用性</b>：Redis 异常时 fail-open（放行 + WARN）；仅 Redis 调用本身被
 * {@code onErrorResume} 覆盖，下游 {@code chain.filter()} 不在其内，确保每次请求恰好分发一次。
 *
 * <p>Filter 链顺序：{@code TraceIdFilter(-1000) → Security Auth → JwtBlacklistFilter(-700)
 * → UserContextHeaderFilter(-600) → AiTokenQuotaFilter(-50) → Route}。
 */
@Slf4j
@Component
public class AiTokenQuotaFilter implements GlobalFilter, Ordered {

    /** AI 导购路径前缀（额度管控范围）。 */
    private static final String AI_PATH_PREFIX = "/api/ai";

    /** 配额键前缀：{@code ai:quota:{u|ip}:<标识>:<yyyy-MM-dd>}。 */
    private static final String QUOTA_KEY_PREFIX = "ai:quota:";

    /** 身份维度键片段：用户 / 客户端 IP。 */
    private static final String KEY_SEGMENT_USER = "u:";
    private static final String KEY_SEGMENT_IP = "ip:";

    /** 网关注入的用户身份头（见 UserContextHeaderFilter）。 */
    private static final String USER_ID_HEADER = SecurityHeaders.X_USER_ID;

    /** 在 UserContextHeaderFilter(-600) 之后、路由之前执行。 */
    private static final int ORDER = -50;

    /** 429 响应体模板（统一错误码 JSON）。 */
    private static final String REJECT_BODY_TEMPLATE = "{\"code\":%d,\"message\":\"%s\"}";

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final long dailyLimit;
    private final int bytesToTokensFactor;
    private final int defaultTokens;
    private final String rejectMessage;

    public AiTokenQuotaFilter(ReactiveRedisTemplate<String, String> redisTemplate,
                              @Value("${gateway.ai-quota.daily-limit:50000}") long dailyLimit,
                              @Value("${gateway.ai-quota.bytes-to-tokens-factor:2}") int bytesToTokensFactor,
                              @Value("${gateway.ai-quota.default-tokens:100}") int defaultTokens,
                              @Value("${gateway.ai-quota.reject-message:AI 助手今日使用额度已达上限，请明天再来}")
                              String rejectMessage) {
        this.redisTemplate = redisTemplate;
        this.dailyLimit = dailyLimit;
        this.bytesToTokensFactor = bytesToTokensFactor;
        this.defaultTokens = defaultTokens;
        this.rejectMessage = rejectMessage;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!isQuotaControlledPath(exchange)) {
            return chain.filter(exchange);
        }

        String key = quotaKey(exchange);
        int cost = estimateTokens(exchange);
        return recordUsage(key, cost)
                .flatMap(used -> used > dailyLimit
                        ? reject(exchange, key, used)
                        : chain.filter(exchange));
    }

    /** 是否属于额度管控范围（{@code /api/ai} 及其子路径）。 */
    private boolean isQuotaControlledPath(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        return path.equals(AI_PATH_PREFIX) || path.startsWith(AI_PATH_PREFIX + "/");
    }

    /** 构造当日配额键：已认证按用户维度，匿名按客户端 IP 维度。 */
    private String quotaKey(ServerWebExchange exchange) {
        String userId = exchange.getRequest().getHeaders().getFirst(USER_ID_HEADER);
        String subject = (userId != null && !userId.isBlank())
                ? KEY_SEGMENT_USER + userId
                : KEY_SEGMENT_IP + EdgePeerIpResolver.resolve(exchange);
        return QUOTA_KEY_PREFIX + subject + ":" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * 原子累加当日用量，并在首次计数时挂上到次日零点的 TTL。
     *
     * @return 累加后的当日用量；Redis 异常时 fail-open 返回 0（视为未用量，放行）
     */
    private Mono<Long> recordUsage(String key, int cost) {
        return redisTemplate.opsForValue().increment(key, cost)
                .flatMap(used -> used != null && used == cost
                        ? expireAtNextMidnight(key).thenReturn(used)
                        : Mono.just(used == null ? 0L : used))
                .onErrorResume(e -> {
                    log.warn("[gateway][ai-quota] 配额计数失败，放行当前请求(fail-open): key={}, error={}",
                            key, e.toString());
                    return Mono.just(0L);
                });
    }

    /** TTL 挂到次日零点（当日额度按自然日重置）。 */
    private Mono<Boolean> expireAtNextMidnight(String key) {
        return redisTemplate.expireAt(key,
                LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /** 估算本次请求的 Token 成本：请求体字节数 × 系数，无 Content-Length 时用默认值。 */
    private int estimateTokens(ServerWebExchange exchange) {
        String contentLength = exchange.getRequest().getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH);
        if (contentLength == null || contentLength.isBlank()) {
            return defaultTokens;
        }
        try {
            return Integer.parseInt(contentLength) * bytesToTokensFactor;
        } catch (NumberFormatException e) {
            log.debug("[gateway][ai-quota] Content-Length 非法，按默认值估算: value={}", contentLength);
            return defaultTokens;
        }
    }

    /** 输出 429 统一错误体，并终止请求（不再进入路由）。 */
    private Mono<Void> reject(ServerWebExchange exchange, String key, long used) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(REJECT_BODY_TEMPLATE, ErrorCode.AI_RATE_LIMITED.getCode(), rejectMessage);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        log.info("[gateway][ai-quota] 配额用尽: key={}, used={}, limit={}", key, used, dailyLimit);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
