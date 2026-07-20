package org.noLazy.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.noLazy.common.domain.UserInfo;
import org.noLazy.common.utils.UserContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 大厂标准: 从 UserContext 桥接到 Spring Security SecurityContext。
 * <p>
 * 网关已完成 JWT 验证，UserContextFilter 已从 Headers 重建 UserInfo。
 * 本 Filter 将 UserInfo 中的 roles/permissions 转换为 Spring Security 的
 * {@link Authentication} + {@link GrantedAuthority}，使 @PreAuthorize 生效。
 * <p>
 * Filter 顺序: 在 UserContextFilter 之后执行 (order = HIGHEST_PRECEDENCE + 20)
 */
public class UserContextAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        UserInfo userInfo = UserContext.getUser();
        if (userInfo != null) {
            List<GrantedAuthority> authorities = new ArrayList<>();

            // 角色 → ROLE_ 前缀的 GrantedAuthority
            if (userInfo.roles() != null) {
                userInfo.roles().forEach(role ->
                        authorities.add(new SimpleGrantedAuthority(role)));
            }

            // 权限 → 直接的 GrantedAuthority（@PreAuthorize("hasAuthority('order:read')")）
            if (userInfo.permissions() != null) {
                userInfo.permissions().forEach(perm ->
                        authorities.add(new SimpleGrantedAuthority(perm)));
            }

            Authentication auth = new UsernamePasswordAuthenticationToken(
                    userInfo, null, authorities);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
