package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.noLazy.common.domain.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 大厂标准: 角色管理 CRUD — 管理员创建/编辑/删除角色，分配权限。
 */
@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
@PreAuthorize("@ss.hasPermi('user:admin')")
public class RoleController {

    // TODO: 注入 RoleService + RoleMapper 实现完整 CRUD
    // 当前为骨架实现（数据库已就绪，Service/Mapper 已存在）

    @GetMapping
    public Result<List<Map<String, Object>>> listRoles() {
        // TODO: roleService.listAll()
        return Result.ok(Collections.emptyList());
    }

    @PostMapping
    public Result<Map<String, Object>> createRole(@RequestBody Map<String, Object> req) {
        // TODO: roleService.create(name, code, description)
        return Result.ok(Map.of("status", "created"));
    }

    @PutMapping("/{id}")
    public Result<Void> updateRole(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        // TODO: roleService.update(id, name, description)
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        // TODO: roleService.delete(id) — 系统角色(is_system=1)不可删除
        return Result.ok();
    }

    @GetMapping("/{id}/permissions")
    public Result<List<Long>> getRolePermissions(@PathVariable Long id) {
        // TODO: permissionMapper.selectPermIdsByRoleId(id)
        return Result.ok(Collections.emptyList());
    }

    @PostMapping("/{id}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long id,
                                          @RequestBody Map<String, List<Long>> req) {
        // TODO: roleService.assignPermissions(id, permissionIds)
        return Result.ok();
    }
}
