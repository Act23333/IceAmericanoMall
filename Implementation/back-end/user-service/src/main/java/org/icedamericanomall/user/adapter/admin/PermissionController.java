package org.icedamericanomall.user.adapter.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.user.app.rbac.PermissionQueryManager;
import org.icedamericanomall.common.domain.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 大厂标准: 权限管理 — 返回权限列表 + 权限树（菜单/按钮/API 三级）。
 *
 * <p>RBAC 五表模型: sys_permission.type: 1=菜单, 2=按钮, 3=API接口。
 * 读模型组装在 {@link PermissionQueryManager}（原 Controller 直连 Mapper 已归位）。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/permissions")
@RequiredArgsConstructor
@PreAuthorize("@ss.hasPermi('user:admin')")
public class PermissionController {

    private final PermissionQueryManager permissionQueryManager;

    @GetMapping
    public Result<List<Map<String, Object>>> listPermissions() {
        return Result.ok(permissionQueryManager.listPermissions());
    }

    @GetMapping("/tree")
    public Result<List<Map<String, Object>>> listPermissionTree() {
        return Result.ok(permissionQueryManager.listPermissionTree());
    }
}
