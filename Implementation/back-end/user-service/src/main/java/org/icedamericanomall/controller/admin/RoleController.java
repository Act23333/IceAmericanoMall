package org.icedamericanomall.controller.admin;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.service.RoleManageService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 角色管理 Interface 层 — DDD COLA 合规
 *
 * 调用链: Controller → RoleManageService(Application) → RoleMapper(Infrastructure)
 * 不直接依赖 Mapper，通过 Service 层解耦。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/roles")
@PreAuthorize("@ss.hasPermi('user:admin')")
public class RoleController {

    private final RoleManageService roleManageService;

    public RoleController(RoleManageService roleManageService) {
        this.roleManageService = roleManageService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> listRoles() {
        return Result.ok(roleManageService.listAllRoles());
    }

    @PostMapping
    public Result<Map<String, Object>> createRole(@RequestBody Map<String, Object> req) {
        String name = (String) req.get("name");
        String code = (String) req.get("code");
        String description = (String) req.getOrDefault("description", "");
        return Result.ok(roleManageService.createRole(name, code, description));
    }

    @PutMapping("/{id}")
    public Result<Void> updateRole(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        String name = (String) req.get("name");
        String description = (String) req.get("description");
        roleManageService.updateRole(id, name, description);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        roleManageService.deleteRole(id);
        return Result.ok();
    }

    @GetMapping("/{id}/permissions")
    public Result<List<Long>> getRolePermissions(@PathVariable Long id) {
        return Result.ok(roleManageService.getRolePermissions(id));
    }

    @PostMapping("/{id}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long id,
                                          @RequestBody Map<String, List<Long>> req) {
        List<Long> permissionIds = req.get("permissionIds");
        if (permissionIds == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "permissionIds 不能为空");
        }
        roleManageService.assignPermissions(id, permissionIds);
        return Result.ok();
    }
}
