package org.icedamericanomall.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * 网关安全配置（WebFlux 响应式安全链）。
 *
 * <p>职责边界：网关只做<b>认证</b>（验 JWT 签名 + 时效 + 吊销）+ 注入用户上下文，
 * <b>不做</b>权限判定（授权由各微服务依据 {@code X-User-Roles} 自行判定）。
 *
 * <p>白名单口径：
 * <ul>
 *   <li>{@link #PUBLIC_WRITE_PATHS} —— 匿名可写的公开端点（登录/注册/发码/第三方回调）；</li>
 *   <li>{@link #PUBLIC_GET_PATHS} —— 仅 GET 匿名放行；同路径写方法（秒杀下单、商品/分类维护、
 *       店铺关注等）一律要求认证。</li>
 * </ul>
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /** 认证服务 JWKS 地址（默认本地开发；生产用环境变量覆盖）。 */
    private final String jwkSetUri;

    /** CORS 放行来源白名单（逗号分隔，仅开发默认值；生产必须显式配置）。 */
    private final List<String> allowedOriginPatterns;

    public SecurityConfig(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:http://localhost:9000/oauth2/jwks}")
            String jwkSetUri,
            @Value("${gateway.cors.allowed-origin-patterns:http://localhost:5173,http://127.0.0.1:5173}")
            List<String> allowedOriginPatterns) {
        this.jwkSetUri = jwkSetUri;
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    /**
     * 匿名可写的公开端点（方法与路径同时匹配）。
     *
     * <p>{@code /api/pay/callback/**} 保持匿名：第三方支付回调不带 JWT，靠签名自证。
     */
    static final String[] PUBLIC_WRITE_PATHS = {
            "/api/auth/web/login",
            "/api/auth/web/wechat/login",
            "/api/auth/wechat/mini/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/reset-password",
            "/api/verification/codes",
            "/api/pay/callback/**"
    };

    /**
     * 浏览类只读路径：<b>仅 GET</b> 匿名放行。
     *
     * <p>同路径的写方法一律落到 {@code authenticated()}，例如
     * {@code POST /api/flash/buy}（秒杀下单）、{@code POST /api/item/spu}（商品维护）、
     * {@code POST /api/item/category}（分类维护）、{@code POST|DELETE /api/shop/follow}（店铺关注）。
     */
    static final String[] PUBLIC_GET_PATHS = {
            "/api/item/**",
            "/api/home/**",
            "/api/search/**",
            "/api/shop/**",
            "/api/shops/**",
            "/api/flash/**",
            "/api/coupon/template",
            "/api/activity/**",
            "/api/currency/**",
            "/api/group-buy/**",
            "/api/notes/**"
    };

    /**
     * JWT 解码器：仅做签名 + 时间戳校验。
     *
     * <p>吊销（jti 黑名单）校验不在解码器内 —— 早期实现于 TokenValidator 中
     * {@code .block()} 等待 Redis，会让每个认证请求阻塞 Netty 事件循环；现已下沉为
     * {@link org.icedamericanomall.gateway.filter.JwtBlacklistFilter}（安全链之后、路由之前，非阻塞）。
     */
    @Bean
    @Primary
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        NimbusReactiveJwtDecoder jwtDecoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<Jwt>(new JwtTimestampValidator());
        jwtDecoder.setJwtValidator(validator);
        return jwtDecoder;
    }

    /**
     * 安全过滤链：CORS 放行 + 方法级白名单 + 其余请求强制认证。
     *
     * <p>WebSocket 握手（{@code /ws/chat/**}）：浏览器发起握手时无法自定义 {@code Authorization}
     * 头，故该路径<b>刻意不进白名单</b>（维持 {@code authenticated()}），由边缘过滤器
     * {@link org.icedamericanomall.gateway.filter.WsHandshakeTokenFilter} 先把 query 中的
     * {@code token} 换写成 {@code Authorization: Bearer} 再进入本安全链 —— 目的路径可达
     * 且鉴权口径与普通接口一致（签名 + 时效 + jti 吊销）。见 ADR-026。
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder reactiveJwtDecoder) {
        http
                .cors(Customizer.withDefaults())
                .authorizeExchange(exchanges -> exchanges
                        // CORS 预检请求全部放行（浏览器 OPTIONS 不带 JWT）
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 匿名可写的公开端点：登录/注册/刷新/发码/第三方支付回调
                        .pathMatchers(PUBLIC_WRITE_PATHS).permitAll()
                        // 浏览类公开端点：仅 GET 放行，写方法落到 authenticated()
                        .pathMatchers(HttpMethod.GET, PUBLIC_GET_PATHS).permitAll()
                        // AI 导购/客服匿名可用（额度由 AiTokenQuotaFilter 按 userId/IP 维度控制）
                        .pathMatchers("/api/ai/**").permitAll()
                        // 其他所有请求都需要认证
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwtSpec -> jwtSpec.jwtDecoder(reactiveJwtDecoder)) // 指定使用自定义的 Decoder
                )
                .csrf(ServerHttpSecurity.CsrfSpec::disable);
        return http.build();
    }

    /**
     * CORS 过滤器：来源白名单来自配置（默认仅本地开发地址）。
     *
     * <p>{@code allowCredentials=true} 时禁止使用通配符 {@code "*"}，否则浏览器会拒绝响应。
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOriginPatterns);
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
