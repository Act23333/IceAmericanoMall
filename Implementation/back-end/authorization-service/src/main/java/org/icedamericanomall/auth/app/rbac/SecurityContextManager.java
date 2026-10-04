package org.icedamericanomall.auth.app.rbac;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.auth.app.port.UserSecurityCache;
import org.icedamericanomall.auth.domain.repository.RoleRepository;
import org.icedamericanomall.common.security.UserInfo;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 用户安全上下文装配（应用层，ADR-018 随 RBAC 归属授权域迁入）。
 *
 * <p>职责：角色/权限聚合 + {@code user:perms} 网关权限缓存写入/预热。
 * 登录成功后预热（网关在下一个请求即可见角色，消除"登录后角色为空"窗口）；
 * 内部查询端点供下游服务（如 user-service 的 /info 聚合）按需回填。
 *
 * <p><b>跨服务契约（网关实时权限依赖，勿改）</b>：写 {@code user:perms:{userId}}
 * （SET，含 ROLE_ 角色与权限），TTL 30 分钟，先删后写。
 */
@Component
@RequiredArgsConstructor
public class SecurityContextManager {

    /** 默认角色 ID —— ROLE_USER（建档即默认角色，原 user-service UserRegistrar 语义迁入） */
    private static final Long DEFAULT_ROLE_ID = 1L;

    private final RoleRepository roleRepository;
    private final UserSecurityCache userSecurityCache;

    /**
     * 加载并回填用户安全上下文（角色 + 聚合权限）。
     *
     * <p>兜底不变式：任何存在用户行的 {@code userId} 至少持有 ROLE_USER
     * （原 UserRegistrar 建档授予语义，随 RBAC 迁入授权域，读时幂等兜底）。
     *
     * @param userId 用户技术主键
     * @return 上下文（roles + permissions）
     */
    public AuthContext loadContext(Long userId) {
        Set<String> roles = roleRepository.findRoleCodesByUserId(userId);
        if (roles.isEmpty()) {
            roleRepository.insertUserRole(userId, DEFAULT_ROLE_ID);
            roles = roleRepository.findRoleCodesByUserId(userId);
        }
        Set<String> perms = new HashSet<>();
        perms.addAll(roleRepository.findDirectPermissionCodesByUserId(userId));
        perms.addAll(roleRepository.findRolePermissionCodesByUserId(userId));

        // 网关实时权限的唯一来源：先删后写（角色以 ROLE_ 前缀并入权限集合）
        Set<String> permissionSet = new HashSet<>(perms);
        permissionSet.addAll(roles);
        userSecurityCache.store(UserInfo.builder()
                        .userId(userId)
                        .username("")
                        .roles(roles)
                        .permissions(perms)
                        .build(),
                permissionSet);

        return new AuthContext(userId, roles, perms);
    }

    /** 登录成功后的缓存预热（消除"登录后角色为空"窗口）。 */
    public void warm(Long userId) {
        loadContext(userId);
    }

    /** 批量查询用户角色编码（管理后台列表派生 roleType）。 */
    public Map<Long, Set<String>> rolesByUserIds(Collection<Long> userIds) {
        return roleRepository.findRoleCodesByUserIds(userIds);
    }

    /** 用户安全上下文（应用层返回值）。 */
    public record AuthContext(Long userId, Set<String> roles, Set<String> permissions) {
    }
}
