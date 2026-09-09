package org.icedamericanomall.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * auth-service 安全配置 — 放行自己的公开端点。
 *
 * spring-boot-starter-security 默认启用 HTTP Basic 保护
 * 所有端点, 导致 login/register 等公开接口返回 401。
 * 此处显式配置放行 /api/auth/**。
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
                        .anyRequest().authenticated()
                )
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
