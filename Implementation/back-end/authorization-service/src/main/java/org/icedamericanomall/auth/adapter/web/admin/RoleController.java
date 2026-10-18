package org.icedamericanomall.auth.adapter.web.admin;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.auth.adapter.request.RolePermissionAssignReq;
import org.icedamericanomall.auth.adapter.request.RoleReq;
import org.icedamericanomall.auth.adapter.request.UpdateRoleReq;
import org.icedamericanomall.auth.adapter.response.RoleVO;
import org.icedamericanomall.auth.app.rbac.RoleManageManager;
import org.icedamericanomall.common.annotation.OperationLog;
import org.icedamericanomall.common.api.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理 Interface 层 — 随 RBAC 归属授权域迁入（ADR-018）。
 *
 * <p>调用链: Controller → RoleManageManager(Application) → RoleRepository(Infrastructure)。
 *
 * <p>入参已对象化（V5.4 结构统一）：{@link RoleReq}（创建）/ {@link UpdateRoleReq}（更新）/
 * {@link RolePermissionAssignReq}（权限分配），替换原先的 {@code Map<String,Object>} 裸入参；
 * URL 与 JSON 字段名保持不变。
 *
 * <p><b>审计（ADR-032 管道一）</b>：四个写操作标注 {@code @OperationLog}，产出单行 JSON 审计日志
 * （logger {@code AUDIT}，经 ELK 采集）；含请求体的端点默认不记参数值（PII 安全默认），
 * 仅路径标识类端点开启参数摘要。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/roles")
// 权限位拆分（5a 批 8，2026-10-18）：RBAC 管理整体挂 rbac:admin（Q11：**含读**）。
// 理由：读"谁能做什么"本身就是敏感信息；且 PermissionBits 的通配符对 rbac:admin 是**排除**的
// —— 它只能被种子显式授予，故这两个控制器是唯一真正的"超管专属面"。
@PreAuthorize("@ss.hasPermi('rbac:admin')")
public class RoleController {

    /** 审计模块：RBAC 角色权限管理。 */
    private static final String MODULE_RBAC = "RBAC";

    /** 审计动作：创建角色。 */
    private static final String ACTION_CREATE_ROLE = "CREATE_ROLE";

    /** 审计动作：更新角色。 */
    private static final String ACTION_UPDATE_ROLE = "UPDATE_ROLE";

    /** 审计动作：删除角色。 */
    private static final String ACTION_DELETE_ROLE = "DELETE_ROLE";

    /** 审计动作：覆盖式分配角色权限。 */
    private static final String ACTION_ASSIGN_PERMISSIONS = "ASSIGN_PERMISSIONS";

    private final RoleManageManager roleManageManager;

    public RoleController(RoleManageManager roleManageManager) {
        this.roleManageManager = roleManageManager;
    }

    /** 全部角色列表。 */
    @GetMapping
    public Result<List<RoleVO>> listRoles() {
        return Result.ok(roleManageManager.listAllRoles().stream().map(RoleVO::from).toList());
    }

    /** 创建角色（name + code 必填，code 唯一）。 */
    @OperationLog(module = MODULE_RBAC, action = ACTION_CREATE_ROLE, description = "创建角色")
    @PostMapping
    public Result<RoleVO> createRole(@Valid @RequestBody RoleReq req) {
        return Result.ok(RoleVO.from(
                roleManageManager.createRole(req.getName(), req.getCode(), req.getDescription())));
    }

    /** 更新角色名称/描述（部分更新，两字段均可选）。 */
    @OperationLog(module = MODULE_RBAC, action = ACTION_UPDATE_ROLE, description = "更新角色")
    @PutMapping("/{id}")
    public Result<Void> updateRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleReq req) {
        roleManageManager.updateRole(id, req.getName(), req.getDescription());
        return Result.ok();
    }

    /** 删除角色（内置角色在 SQL 层拒绝）。 */
    @OperationLog(module = MODULE_RBAC, action = ACTION_DELETE_ROLE, description = "删除角色",
            recordParams = true)
    @DeleteMapping("/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        roleManageManager.deleteRole(id);
        return Result.ok();
    }

    /** 角色已分配的权限 ID 列表。 */
    @GetMapping("/{id}/permissions")
    public Result<List<Long>> getRolePermissions(@PathVariable Long id) {
        return Result.ok(roleManageManager.getRolePermissions(id));
    }

    /** 覆盖式分配角色权限（permissionIds 必传，空数组表示清空）。 */
    @OperationLog(module = MODULE_RBAC, action = ACTION_ASSIGN_PERMISSIONS, description = "分配角色权限")
    @PostMapping("/{id}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long id,
                                          @Valid @RequestBody RolePermissionAssignReq req) {
        roleManageManager.assignPermissions(id, req.getPermissionIds());
        return Result.ok();
    }
}
