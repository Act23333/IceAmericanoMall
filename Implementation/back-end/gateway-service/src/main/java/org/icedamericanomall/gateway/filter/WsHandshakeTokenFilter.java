package org.icedamericanomall.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * WebSocket 握手令牌边缘换头过滤器（order = -900 —— 早于 Spring Security 安全链）。
 *
 * <p><b>解决的问题</b>：浏览器发起 WebSocket 握手（{@code new WebSocket(...)} / SockJS）时
 * <b>无法自定义 {@code Authorization} 请求头</b>，而 {@code /ws/chat/**} 在
 * {@link org.icedamericanomall.gateway.config.SecurityConfig} 中落到 {@code authenticated()}，
 * 结果握手必然 401。约定的替代通道是把令牌放在 query 参数里
 * （{@code /ws/chat/.../websocket?token=<accessToken>}）。
 *
 * <p><b>本过滤器的职责（只做搬运，不做鉴权）</b>：对配置路径（{@code gateway.ws.token-paths}）
 * 且<b>入站无 {@code Authorization}</b> 且 <b>query 含 {@value #TOKEN_QUERY_PARAM}</b> 时，
 * 把令牌值改写为 {@code Authorization: Bearer <token>} 请求头，并<b>重建向下游的请求 URI，
 * 剔除 query 中的令牌参数</b>（令牌不得出现在下游服务入参、下游访问日志或反代日志里）。
 *
 * <p><b>鉴权仍然交给既有安全链</b>：换头后由 Spring Security 验签 + 时效校验
 * （{@code /ws/chat/**} 维持 {@code authenticated()}，<b>不加入白名单</b>），再由
 * {@link JwtBlacklistFilter} 做 jti 吊销校验，最后由
 * {@link UserContextHeaderFilter} 注入 {@code X-User-Id} 等可信上下文头 ——
 * ai-service 的握手拦截器依赖网关注入的身份头，下游一行代码不改。
 *
 * <p><b>为什么必须是 {@link WebFilter} 且 order 早于安全链</b>：Spring Cloud Gateway 的
 * {@code GlobalFilter}（sanitizer/trace 等）由 {@code FilteringWebHandler} 在路由阶段执行，
 * <b>晚于</b>所有 {@code WebFilter}；若把换头写成 {@code GlobalFilter}，Security 已经先返回 401。
 * Spring Security 的安全链（{@code WebFilterChainProxy}）order 为 {@code -100}
 * （{@code WebFluxSecurityConfiguration.WEB_FILTER_CHAIN_FILTER_ORDER}），故取 {@code -900}。
 *
 * <p><b>令牌暴露面</b>：query 中的令牌在网关入口的访问日志 / 浏览器历史里可见（WS 握手的固有权衡，
 * 取舍与缓解见 ADR-026）。
 */
@Slf4j
@Component
public class WsHandshakeTokenFilter implements WebFilter, Ordered {

    /** 握手令牌的 query 参数名（与前端约定；详见 ADR-026）。 */
    public static final String TOKEN_QUERY_PARAM = "token";

    /** Bearer 令牌前缀（与 OAuth2 资源服务器口径一致）。 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** query 参数对之间的分隔符。 */
    private static final String QUERY_PAIR_DELIMITER = "&";

    /** query 参数键值分隔符。 */
    private static final char QUERY_KEY_VALUE_DELIMITER = '=';

    /** 百分号转义起始字符（用于判断参数名是否需要解码后比较）。 */
    private static final char PERCENT_ESCAPE = '%';

    /**
     * 过滤器顺序：Spring Security 安全链为 {@code -100}，换头必须发生在认证之前。
     *
     * <p>本过滤器是应用中唯一的 {@code -900} 序位 WebFilter（网关其余过滤器均为
     * 路由阶段的 {@code GlobalFilter}，天然晚于全部 {@code WebFilter}）。
     */
    private static final int ORDER = -900;

    /** 生效路径模式（{@code gateway.ws.token-paths}，逗号分隔，默认 {@code /ws/chat/**}）。 */
    private final List<PathPattern> tokenPaths;

    public WsHandshakeTokenFilter(@Value("${gateway.ws.token-paths:/ws/chat/**}") List<String> tokenPaths) {
        PathPatternParser parser = PathPatternParser.defaultInstance;
        this.tokenPaths = tokenPaths.stream()
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .map(parser::parse)
                .toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!isTokenPath(request) || request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            return chain.filter(exchange);
        }
        String token = request.getQueryParams().getFirst(TOKEN_QUERY_PARAM);
        if (token == null || token.isBlank()) {
            // 无令牌：原样放行，是否 401 由后续安全链决定
            return chain.filter(exchange);
        }

        ServerHttpRequest handshakeRequest = request.mutate()
                .uri(removeTokenParam(request.getURI()))
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token)
                .build();
        log.debug("[gateway][ws-handshake] 握手令牌换头完成并剔除 query 令牌: path={}",
                request.getPath().pathWithinApplication().value());
        return chain.filter(exchange.mutate().request(handshakeRequest).build());
    }

    /** 请求路径是否落在配置的握手令牌路径模式内。 */
    private boolean isTokenPath(ServerHttpRequest request) {
        return tokenPaths.stream().anyMatch(pattern ->
                pattern.matches(request.getPath().pathWithinApplication()));
    }

    /**
     * 重建 URI：剔除 query 中的令牌参数，其余参数逐字保留（不二次编码）。
     *
     * @param origin 原始请求 URI
     * @return 不含令牌参数的 URI；原 query 为空时原样返回
     */
    private URI removeTokenParam(URI origin) {
        String rawQuery = origin.getRawQuery();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return origin;
        }
        String remainingQuery = Arrays.stream(rawQuery.split(QUERY_PAIR_DELIMITER))
                .filter(pair -> !isTokenPair(pair))
                .collect(Collectors.joining(QUERY_PAIR_DELIMITER));
        // build(true)：remainingQuery 取自原 rawQuery（已是编码态），禁止二次编码（避免 %2F 被转成 %252F）
        return UriComponentsBuilder.fromUri(origin)
                .replaceQuery(remainingQuery.isEmpty() ? null : remainingQuery)
                .build(true)
                .toUri();
    }

    /** 单个 query 参数对是否为令牌参数（参数名允许被百分号编码，如 {@code %74oken}）。 */
    private boolean isTokenPair(String pair) {
        int delimiterIndex = pair.indexOf(QUERY_KEY_VALUE_DELIMITER);
        String rawKey = delimiterIndex >= 0 ? pair.substring(0, delimiterIndex) : pair;
        if (TOKEN_QUERY_PARAM.equals(rawKey)) {
            return true;
        }
        if (rawKey.indexOf(PERCENT_ESCAPE) < 0) {
            return false;
        }
        try {
            return TOKEN_QUERY_PARAM.equals(UriUtils.decode(rawKey, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            // 参数名非法转义：按非令牌参数处理，交由下游/安全链判定
            return false;
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
