package org.icedamericanomall.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * JWT 吊销校验过滤器（jti 黑名单 + 用户级令牌版本，一次 Redis 查询完成）—— 非阻塞实现，order = -700。
 *
 * <p><b>为什么不在 TokenValidator 里做</b>：早期实现于 {@code JwtBlacklistValidator.validate()}
 * 中对 Reactive Redis 的结果调用 {@code .block()}，而该方法运行在 Netty 事件循环线程上——
 * 每个带 token 的请求都会同步等待一次 Redis，Redis 抖动即全站排队（Reactor 还会抛
 * {@code IllegalStateException: block()/blockFirst()/blockLast() are blocking, which is not
 * supported in thread reactor-http-nio-*}）。现改为安全过滤链中的 {@link GlobalFilter}：
 * 认证完成后（安全上下文已就绪）、路由之前，用 {@link ReactiveStringRedisTemplate} 异步查询。
 *
 * <p><b>两级吊销（ADR-031）合并为一次调用</b>：单次 {@code MGET} 同时取
 * {@code auth:blacklist:{jti}}（单 token 粒度，登出写）与 {@code auth:tokenver:{userId}}
 * （用户级粒度，改密 / 重置密码 / 管理端禁用写）。每请求的 Redis 往返次数与仅查黑名单时相同。
 * <ul>
 *   <li>黑名单命中（值非空）→ 401；</li>
 *   <li>版本键存在 → 与 token 的 {@code ver} claim 比较（claim 缺失 / 类型异常一律按 {@code 0}），
 *       不相等 → 401；</li>
 *   <li>版本键缺失 → 视为"从未被用户级吊销"，跳过版本比对（放行）；</li>
 *   <li>token 无 {@code userId} claim → 只查黑名单，跳过版本比对；</li>
 *   <li>token 无 {@code jti} → 维持原语义整体放行（无法比对，记 DEBUG）。</li>
 * </ul>
 * 兼容规则：存量令牌无 {@code ver} claim（= {@code 0}）在用户从未被吊销时自然可用至过期，无需迁移。
 *
 * <p><b>可用性权衡</b>：Redis 异常时 <b>fail-open</b>（放行 + WARN 日志）——吊销名单是
 * 「登录态失效」的加速器，而非唯一防线：令牌自身仍受签名 + 过期时间校验约束。
 * 若业务要求「Redis 不可用时一律拒绝」，可改为 fail-closed（拒绝 + 503），或为校验结果
 * 增加本地 Caffeine 短 TTL 缓存（降级后最多容忍 TTL 窗口内的已吊销 token）。
 *
 * <p>Filter 链顺序：{@code UntrustedHeaderSanitizerFilter(-1100) → TraceIdFilter(-1000)
 * → Spring Security 认证 → JwtBlacklistFilter(-700) → UserContextHeaderFilter(-600) → Route}。
 */
@Slf4j
@Component
public class JwtBlacklistFilter implements GlobalFilter, Ordered {

    /** 单 token 吊销名单键前缀（authorization-service 登出时写入，ADR-027）。 */
    private static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";

    /** 用户级令牌版本键前缀（authorization-service 改密/禁用时 INCR，ADR-031）。 */
    private static final String TOKEN_VERSION_KEY_PREFIX = "auth:tokenver:";

    /** JWT 中的用户 ID claim（与 {@code UserContextHeaderFilter} 同口径）。 */
    private static final String CLAIM_USER_ID = "userId";

    /** JWT 中的用户级令牌版本 claim（缺失按 {@code 0}）。 */
    private static final String CLAIM_TOKEN_VERSION = "ver";

    /** claim / Redis 值不可解析时的兜底版本（与"键缺失 = 版本 0"兼容规则一致）。 */
    private static final long ABSENT_VERSION = 0L;

    /** Redis 版本值不可解析时的哨兵：跳过版本比对（fail-open，不误杀整站令牌）。 */
    private static final long UNPARSEABLE_VERSION = -1L;

    /** 认证之后（安全上下文可用）、用户上下文注入之前。 */
    private static final int ORDER = -700;

    /** 401 响应体模板（网关层不接受 Result 包装依赖，直接输出统一错误码 JSON）。 */
    private static final String REJECT_BODY_TEMPLATE = "{\"code\":%d,\"message\":\"%s\"}";

    /** 令牌已吊销的提示语（对客户端统一话术，不暴露内部细节）。 */
    private static final String REJECT_MESSAGE = "登录状态已失效，请重新登录";

    private final ReactiveStringRedisTemplate redisTemplate;

    public JwtBlacklistFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(auth -> auth.getAuthentication())
                .filter(this::isJwtAuthentication)
                .map(auth -> (Jwt) auth.getPrincipal())
                .flatMap(this::isRevoked)
                // 匿名请求（无 JWT）或解析失败 → 视为未吊销，交给后续过滤器/授权规则处理
                .defaultIfEmpty(Boolean.FALSE)
                .flatMap(revoked -> Boolean.TRUE.equals(revoked)
                        ? reject(exchange)
                        : chain.filter(exchange));
    }

    /**
     * 是否携带已解析的 JWT 身份。
     *
     * <p>只判断 principal 类型：在资源服务器流程中，principal 为 {@link Jwt} 即代表令牌已通过
     * 签名与时效校验；不额外要求 {@code isAuthenticated()}，避免任何构造差异导致吊销校验被跳过。
     */
    private boolean isJwtAuthentication(Authentication auth) {
        return auth != null && auth.getPrincipal() instanceof Jwt;
    }

    /**
     * 查询令牌是否已被吊销（黑名单 + 用户级版本，一次 {@code MGET}）。
     *
     * @param jwt 已通过签名与时效校验的令牌
     * @return {@code true} 表示已吊销；Redis 异常时 fail-open 返回 {@code false}
     */
    private Mono<Boolean> isRevoked(Jwt jwt) {
        String jti = jwt.getId();
        if (jti == null || jti.isBlank()) {
            log.debug("[gateway][jwt-revocation] token 无 jti，跳过吊销校验: sub={}", jwt.getSubject());
            return Mono.just(Boolean.FALSE);
        }
        Long userId = claimUserId(jwt);
        List<String> keys = new ArrayList<>(2);
        keys.add(BLACKLIST_KEY_PREFIX + jti);
        if (userId != null) {
            keys.add(TOKEN_VERSION_KEY_PREFIX + userId);
        } else {
            log.debug("[gateway][jwt-revocation] token 无 userId claim，仅校验黑名单: jti={}", jti);
        }

        return redisTemplate.opsForValue().multiGet(keys)
                .map(values -> isRevoked(jwt, values))
                .defaultIfEmpty(Boolean.FALSE)
                .onErrorResume(e -> {
                    log.warn("[gateway][jwt-revocation] 吊销名单查询失败，放行当前请求(fail-open): jti={}, error={}",
                            jti, e.toString());
                    return Mono.just(Boolean.FALSE);
                });
    }

    /**
     * 依据 {@code MGET} 结果判定是否吊销：下标 0 = 黑名单键，下标 1（存在时）= 令牌版本键。
     *
     * @param jwt    当前令牌（读取 {@code ver} claim）
     * @param values {@code MGET} 结果（键缺失对应 {@code null}）
     */
    private boolean isRevoked(Jwt jwt, List<String> values) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        if (values.get(0) != null) {
            log.debug("[gateway][jwt-revocation] jti 命中黑名单: jti={}", jwt.getId());
            return true;
        }
        if (values.size() < 2) {
            return false;
        }
        String versionValue = values.get(1);
        if (versionValue == null || versionValue.isBlank()) {
            // 键缺失 = 该用户从未被用户级吊销，跳过版本比对
            return false;
        }
        long currentVersion = parseRedisVersion(versionValue);
        if (currentVersion < 0) {
            return false;
        }
        long tokenVersion = tokenVersion(jwt);
        if (tokenVersion != currentVersion) {
            log.info("[gateway][jwt-revocation] 令牌版本落后，拒绝: jti={}, tokenVer={}, currentVer={}",
                    jwt.getId(), tokenVersion, currentVersion);
            return true;
        }
        return false;
    }

    /**
     * 解析 Redis 版本值（{@code INCR} 产出的十进制长整型）。
     *
     * @return 不可解析时返回 {@link #UNPARSEABLE_VERSION}（调用方跳过比对，fail-open）
     */
    private long parseRedisVersion(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("[gateway][jwt-revocation] 版本键值不可解析，跳过版本比对(fail-open): value={}", value);
            return UNPARSEABLE_VERSION;
        }
    }

    /**
     * 读取 token 的 {@code userId} claim。
     *
     * <p>JSON 反序列化可能给出 {@link Integer} 或 {@link Long}，这里统一按数值语义归一为 {@link Long}；
     * 缺失或不可解析时返回 {@code null}（调用方退化为只查黑名单）。
     */
    private Long claimUserId(Jwt jwt) {
        Object raw = jwt.getClaim(CLAIM_USER_ID);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(raw.toString().trim());
        } catch (NumberFormatException e) {
            log.debug("[gateway][jwt-revocation] userId claim 不可解析，跳过版本比对: jti={}, value={}",
                    jwt.getId(), raw);
            return null;
        }
    }

    /**
     * 读取 token 的 {@code ver} claim。
     *
     * <p>claim 缺失或类型异常一律按 {@link #ABSENT_VERSION}（{@code 0}）处理，
     * 与「无 ver 的存量令牌 = 版本 0」的兼容规则一致。
     */
    private long tokenVersion(Jwt jwt) {
        Object raw = jwt.getClaim(CLAIM_TOKEN_VERSION);
        if (raw == null) {
            return ABSENT_VERSION;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(raw.toString().trim());
        } catch (NumberFormatException e) {
            log.debug("[gateway][jwt-revocation] ver claim 类型异常，按缺失(0)处理: jti={}, value={}",
                    jwt.getId(), raw);
            return ABSENT_VERSION;
        }
    }

    /** 输出 401 统一错误体，并终止请求（不再进入路由）。 */
    private Mono<Void> reject(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(REJECT_BODY_TEMPLATE, ErrorCode.TOKEN_EXPIRED.getCode(), REJECT_MESSAGE);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        log.info("[gateway][jwt-revocation] 拒绝已吊销令牌: path={}", exchange.getRequest().getURI().getPath());
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
