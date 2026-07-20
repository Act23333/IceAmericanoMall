package org.noLazy.common.config;

import org.noLazy.common.security.UserContextAuthenticationFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 大厂标准: 下游服务方法级安全配置。
 * <p>
 * 鉴权 (Authentication) 在网关完成 → 下游服务只做授权 (Authorization)。
 * 通过 {@link UserContextAuthenticationFilter} 将网关传递的 UserInfo
 * 桥接到 Spring Security SecurityContext，使 @PreAuthorize 生效。
 * <p>
 * 仅在存在 spring-security 依赖时激活。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // 启用 @PreAuthorize / @PostAuthorize / @Secured
@ConditionalOnClass(name = "org.springframework.security.config.annotation.web.configuration.EnableWebSecurity")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MethodSecurityConfig {

    @Bean
    public SecurityFilterChain methodSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 鉴权在网关已完成，下游服务全部放行（授权由 @PreAuthorize 控制）
                        .anyRequest().permitAll()
                )
                .addFilterBefore(new UserContextAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
