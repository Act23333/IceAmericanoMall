package org.icedamericanomall.user.adapter.admin;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.user.app.rbac.RoleManageManager;
import org.icedamericanomall.common.domain.Result;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 角色管理 Interface 层 — DDD COLA 合规
 *
 * 调用链: Controller → RoleManageManager(Application) → RoleMapper(Infrastructure)
 * 不直接依赖 Mapper，通过 Service 层解耦。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/roles")
@PreAuthorize("@ss.hasPermi('user:admin')")
public class RoleController {

    private final RoleManageManager roleManageManager;

    public RoleController(RoleManageManager roleManageManager) {
        this.roleManageManager = roleManageManager;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> listRoles() {
        return Result.ok(roleManageManager.listAllRoles());
    }

    @PostMapping
    public Result<Map<String, Object>> createRole(@RequestBody Map<String, Object> req) {
        String name = (String) req.get("name");
        String code = (String) req.get("code");
        String description = (String) req.getOrDefault("description", "");
        return Result.ok(roleManageManager.createRole(name, code, description));
    }

    @PutMapping("/{id}")
    public Result<Void> updateRole(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        String name = (String) req.get("name");
        String description = (String) req.get("description");
        roleManageManager.updateRole(id, name, description);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        roleManageManager.deleteRole(id);
        return Result.ok();
    }

    @GetMapping("/{id}/permissions")
    public Result<List<Long>> getRolePermissions(@PathVariable Long id) {
        return Result.ok(roleManageManager.getRolePermissions(id));
    }

    @PostMapping("/{id}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long id,
                                          @RequestBody Map<String, List<Long>> req) {
        List<Long> permissionIds = req.get("permissionIds");
        if (permissionIds == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "permissionIds 不能为空");
        }
        roleManageManager.assignPermissions(id, permissionIds);
        return Result.ok();
    }
}
