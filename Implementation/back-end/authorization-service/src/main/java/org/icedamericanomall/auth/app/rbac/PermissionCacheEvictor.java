package org.icedamericanomall.auth.app.rbac;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.auth.app.port.UserSecurityCache;
import org.icedamericanomall.auth.domain.repository.RoleRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

/**
 * 权限缓存主动失效器（应用层，ADR-018 随 RBAC 归属授权域迁入）—— 授权数据变更后的缓存收敛点。
 *
 * <p>解决的问题：网关实时权限读 {@code user:perms:{userId}}（TTL 30 分钟），
 * 若只靠 TTL 兜底，撤销的角色/权限最长 30 分钟内仍然生效。所有写授权表的路径
 * （sys_user_role / sys_role_permission / sys_user_permission）变更后必须调用本类，
 * 主动删除受影响用户的 {@code user:perms:} 与 {@code user:info:} 缓存。
 *
 * <p>失效口径：
 * <ul>
 *   <li>用户维度变更（设置用户角色 / 用户直授权限）→ 直接逐用户失效；</li>
 *   <li>角色维度变更（角色权限分配 / 角色删除）→ 按 role_id 反查 sys_user_role
 *       得到受影响用户集合后逐个失效。</li>
 * </ul>
 * 失效失败只告警不抛（缓存可自愈：旧值最多再存活 TTL，且删除是幂等操作）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionCacheEvictor {

    private final RoleRepository roleRepository;
    private final UserSecurityCache userSecurityCache;

    /**
     * 失效指定用户的权限缓存（用户角色 / 用户直授权限变更后调用）。
     *
     * @param userId 用户技术主键
     */
    public void evictUser(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            userSecurityCache.evict(userId);
            log.info("[rbac][perm-cache][evict-user] userId={}", userId);
        } catch (Exception e) {
            log.warn("[rbac][perm-cache][evict-user][fail] userId={}", userId, e);
        }
    }

    /**
     * 失效持有指定角色的全部用户的权限缓存（角色权限分配 / 角色删除后调用）。
     *
     * @param roleId 角色 ID
     */
    public void evictByRole(Long roleId) {
        if (roleId == null) {
            return;
        }
        Set<Long> userIds = roleRepository.findUserIdsByRoleId(roleId);
        for (Long userId : userIds) {
            evictUser(userId);
        }
        log.info("[rbac][perm-cache][evict-role] roleId={}, affectedUsers={}", roleId, userIds.size());
    }

    /**
     * 失效一批用户的权限缓存（批量场景兜底入口）。
     *
     * @param userIds 用户技术主键集合
     */
    public void evictUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (Long userId : userIds) {
            evictUser(userId);
        }
    }
}
