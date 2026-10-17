package org.icedamericanomall.auth.app.rbac;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.auth.app.port.UserSecurityCache;
import org.icedamericanomall.auth.domain.model.rbac.Role;
import org.icedamericanomall.auth.domain.repository.RoleRepository;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RBAC 角色管理（应用层）—— 角色 CRUD + 角色权限分配 + 用户角色设置。
 * （归属自 user-service 迁入，ADR-018；读模型已强类型化）
 *
 * <p>调用链: Adapter → 本类（Application） → {@code RoleRepository}（Infrastructure），
 * 不直接依赖 Mapper。内置角色保护由 SQL 层（{@code is_system=0}）承担。
 *
 * <p><b>权限缓存失效</b>：本类是授权写路径的唯一入口（sys_user_role /
 * sys_role_permission 的写入都经此处），每次写后调用 {@link PermissionCacheEvictor}
 * 主动删除受影响用户的 {@code user:perms:{userId}} / {@code user:info:{userId}} 缓存；
 * 角色删除（级联删 sys_user_role）在删除<b>前</b>取受影响用户集合，删除后逐个失效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleManageManager {

    /** roleType → sys_role.id 映射: 0→1(USER), 1→3(SELLER), 2→4(ADMIN) */
    private static final Long[] ROLE_MAP = {1L, 3L, 4L};

    /** 角色 ID —— ROLE_USER（商家保留普通用户角色） */
    private static final Long ROLE_USER_ID = 1L;

    private final RoleRepository roleRepository;
    private final PermissionCacheEvictor permissionCacheEvictor;

    /** 全部角色。 */
    public List<Role> listAllRoles() {
        return roleRepository.findAllRoles();
    }

    /** 新建角色。 */
    public Role createRole(String name, String code, String description) {
        if (name == null || name.isBlank() || code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "角色名称和编码不能为空");
        }
        Role role = roleRepository.insertRole(name, code, description);
        log.info("角色创建成功: name={}, code={}", name, code);
        return role;
    }

    /**
     * 更新角色名称/描述。
     *
     * <p><b>错误语义（批次 ①「not-found 归位」）</b>：受影响行数为 0 = <b>角色不存在</b>
     * （{@code sys_role} 无 {@code is_system} 条件，故不存在其它 0 行原因）→
     * {@link ErrorCode#NO_RESOURCE_FOUND}（1103 / <b>HTTP 404</b>）。
     * 改造前借用 {@code BUSINESS_EXECUTION_EXCEPTION}（3000 / 200），把「资源不存在」
     * 伪装成了「业务规则拒绝」，前端拿不到可区分的存在性信号。
     */
    public void updateRole(Long id, String name, String description) {
        int rows = roleRepository.updateRole(id, name, description);
        if (rows <= 0) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "角色不存在");
        }
        log.info("角色更新成功: id={}", id);
    }

    /**
     * 删除角色（内置角色在 SQL 层拒绝）。
     *
     * <p>删除会级联清空 sys_user_role 中该角色的行 —— 受影响用户集合必须在删除<b>前</b>取，
     * 删除后逐个失效权限缓存（否则被删角色的权限会残留到 TTL 到期）。
     *
     * <p><b>单义拆分（批次 ①，逐条阅读）</b>：删除 SQL 带 {@code is_system = 0}，故
     * 「受影响行数 = 0」此前同时承载两种语义。现按 {@code 09-constraints.md} §三·六 拆开：
     * <table border="1">
     *   <caption>两种语义 → 两种承载</caption>
     *   <tr><th>语义</th><th>抛出</th><th>HTTP</th></tr>
     *   <tr>
     *     <td><b>角色不存在</b>（查不到该 id）</td>
     *     <td>{@link ErrorCode#NO_RESOURCE_FOUND}（1103）</td>
     *     <td><b>404</b>（资源不存在）</td>
     *   </tr>
     *   <tr>
     *     <td><b>内置角色不可删</b>（角色存在、{@code is_system = 1}）</td>
     *     <td>{@link ErrorCode#BUSINESS_EXECUTION_EXCEPTION}（3000）</td>
     *     <td><b>200</b>（业务规则拒绝：资源存在、请求合法、规则不允许）</td>
     *   </tr>
     * </table>
     * 「内置不可删」<b>不</b>改成 4xx：角色确实存在，用 404 会让运营把「系统保护」误读成「角色被删了」。
     *
     * @throws BizException 角色不存在（NO_RESOURCE_FOUND / 404）或系统内置角色（3000 / 200）
     */
    public void deleteRole(Long id) {
        Role role = roleRepository.findById(id);
        if (role == null) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "角色不存在");
        }
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "系统内置角色不可删除");
        }
        Set<Long> affectedUserIds = new HashSet<>(roleRepository.findUserIdsByRoleId(id));
        int rows = roleRepository.deleteRole(id);
        if (rows <= 0) {
            // 前置校验已排除「不存在」与「内置」：此处只剩并发（他人刚删掉）——
            // 目标确实已不存在，按 not-found 口径应答，绝不冒称「内置不可删」。
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "角色不存在");
        }
        permissionCacheEvictor.evictUsers(affectedUserIds);
        log.info("角色删除成功: id={}, 受影响用户数={}", id, affectedUserIds.size());
    }

    /** 角色已分配的权限 ID 列表。 */
    public List<Long> getRolePermissions(Long roleId) {
        return roleRepository.findPermissionIdsByRoleId(roleId);
    }

    /** 覆盖式分配角色权限（先清后建）；变更后失效持有该角色用户的权限缓存。 */
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        if (permissionIds == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "permissionIds 不能为空");
        }
        roleRepository.deleteRolePermissions(roleId);
        for (Long permId : permissionIds) {
            roleRepository.insertRolePermission(roleId, permId);
        }
        // 角色权限变更 → 按 role_id 反查受影响用户，逐个删 user:perms:{userId}
        permissionCacheEvictor.evictByRole(roleId);
        log.info("角色权限分配成功: roleId={}, permCount={}", roleId, permissionIds.size());
    }

    /**
     * 设置用户角色（覆盖式；商家同时保留 ROLE_USER）；变更后失效该用户权限缓存。
     *
     * <p>V5.0: roleType → sys_role.id 映射: 0→1(USER), 1→3(SELLER), 2→4(ADMIN)。
     * <p>V5.2: 只写 sys_user_role（五表为唯一权威口径），不再写 user.role_type（列退役）。
     */
    public void setUserRole(Long userId, Integer roleType) {
        if (roleType == null || roleType < 0 || roleType >= ROLE_MAP.length) {
            throw new BizException(ErrorCode.PARAM_ERROR, "无效的角色类型: " + roleType);
        }
        roleRepository.deleteUserRoles(userId);
        roleRepository.insertUserRole(userId, ROLE_MAP[roleType]);
        if (roleType == 1) {
            roleRepository.insertUserRole(userId, ROLE_USER_ID);
        }
        permissionCacheEvictor.evictUser(userId);
        log.info("用户角色已更新: userId={}, roleType={}", userId, roleType);
    }
}
