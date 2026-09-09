package org.icedamericanomall.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                .cors(Customizer.withDefaults())
                .authorizeExchange(exchanges -> exchanges
                        // CORS 预检请求全部放行（浏览器 OPTIONS 不带 JWT）
                        .pathMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        // 内部服务间调用路径 — 网关直接放行（生产环境需网络隔离）在微服务架构中，网关不仅仅是外部流量的入口，更应被视为整个系统流量的中央管控点。让内部请求也经过网关，本质上是为了标准化和集中化所有流量的治理
//                        .pathMatchers("/internal/**").permitAll()
                        // 公开路径：认证、注册、支付回调
                        .pathMatchers(
                                "/api/auth/login",
                                "/api/auth/login/phone",
                                "/api/auth/login/wechat",
                                "/api/auth/register",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/api/auth/reset-password",
                                "/api/auth/captcha/sms",
                                "/api/user/code",
                                "/api/pay/callback/wechat",
                                "/api/pay/callback/**"
                        ).permitAll()
                        // 商品浏览公开（列表、详情、分类、评价、店铺、秒杀、领券中心）+ AI + 首页
                        .pathMatchers(
                                "/api/item/category/**",
                                "/api/item/product/**",
                                "/api/item/review/product/**",
                                "/api/home/**",
                                "/api/search/**",
                                "/api/shop/**",
                                "/api/flash",
                                "/api/flash/**",
                                "/api/coupon/template",
                                "/api/ai/**"
                        ).permitAll()
                        // 其他所有请求都需要认证
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .csrf(ServerHttpSecurity.CsrfSpec::disable);
        return http.build();
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
