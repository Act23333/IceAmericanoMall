package org.icedamericanomall.auth.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * auth-service 安全配置 — 放行自己的公开端点。
 *
 * spring-boot-starter-security 默认启用 HTTP Basic 保护
 * 所有端点, 导致 login/register 等公开接口返回 401。
 * 此处显式配置放行 /api/auth/**。
 *
 * <p>同时提供 {@link PasswordEncoder}（BCrypt）Bean：
 * 领域端口 {@code PasswordVerifier} 的适配器依赖它。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // 认证服务自己的端点全部公开
                        .requestMatchers("/api/auth/**", "/oauth2/**").permitAll()
                        // 内部健康检查
                        .requestMatchers("/actuator/**").permitAll()
                        // 服务间 Feign 内部端点（RBAC/授权上下文，生产环境网络隔离）
                        .requestMatchers("/internal/**").permitAll()
                        .anyRequest().authenticated()
                )
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }

    /** 密码哈希器：统一 BCrypt，与 user-service 保持一致 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
