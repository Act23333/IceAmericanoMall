package org.icedamericanomall.user.app.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.AuthContextClient;
import org.icedamericanomall.dto.AuthContextCO;
import org.icedamericanomall.user.adapter.response.UserInfoResp;
import org.icedamericanomall.user.app.rbac.RoleTypeDerivation;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 用户安全上下文装配（应用层，ADR-018）—— 角色/权限经 ia-api {@link AuthContextClient}
 * 稳定契约调 authorization-service（RBAC 五表归属授权域，本服务不再直读 sys_* 表）。
 *
 * <p>{@code user:perms / user:info} 缓存的写入与失效由 authorization-service 负责
 * （登录预热 + 授权变更失效 + 本契约调用时回填）；远端不可用时降级返回空集合
 * ——表现为"无角色/无权限"，绝不默认放行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserInfoManager {

    private final AuthContextClient authContextClient;
    private final UserQueryManager userQueryManager;

    /**
     * 装配当前用户信息 + RBAC 上下文（公开端点 {@code GET /api/user/info} 专用）。
     *
     * <p>先取基础资料（用户不存在 / 已冻结即抛业务异常），再合并角色与权限；
     * 角色/权限远端不可用时由 {@link #loadContext(Long)} 降级为空集合，
     * 不阻断基本信息返回。编排细节（降级、roleType 派生）收敛于此，Controller 一行调用。
     *
     * @param userId 当前登录用户 ID
     * @return 含角色与权限的用户信息
     */
    public UserInfoResp getUserInfoWithRbac(Long userId) {
        UserInfoResp resp = userQueryManager.getByUserId(userId);
        AuthContextCO context = loadContext(userId);
        if (context.getRoles() != null && !context.getRoles().isEmpty()) {
            resp.setRoles(context.getRoles());
            // V5.2: roleType 兼容字段 —— 由五表角色编码派生（不再读 user.role_type 列）
            resp.setRoleType(RoleTypeDerivation.fromRoleCodes(context.getRoles()));
        }
        if (context.getPermissions() != null && !context.getPermissions().isEmpty()) {
            resp.setPermissions(context.getPermissions());
        }
        return resp;
    }

    /**
     * 读取用户授权上下文（角色 + 权限；远端同时回填网关权限缓存）。
     *
     * <p>降级语义：远端不可用返回空集合（调用方据此填充响应，不阻断基本信息返回）。
     */
    public AuthContextCO loadContext(Long userId) {
        try {
            AuthContextCO context = authContextClient.context(userId);
            if (context == null) {
                return emptyContext(userId);
            }
            return AuthContextCO.builder()
                    .userId(userId)
                    .roles(context.getRoles() == null ? Set.of() : context.getRoles())
                    .permissions(context.getPermissions() == null ? Set.of() : context.getPermissions())
                    .build();
        } catch (Exception e) {
            log.warn("[user][rbac][load][fail] userId={}: {}", userId, e.getMessage());
            return emptyContext(userId);
        }
    }

    private AuthContextCO emptyContext(Long userId) {
        return AuthContextCO.builder()
                .userId(userId)
                .roles(Set.of())
                .permissions(Set.of())
                .build();
    }
}
