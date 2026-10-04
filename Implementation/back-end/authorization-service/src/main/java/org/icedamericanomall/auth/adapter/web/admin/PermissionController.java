package org.icedamericanomall.auth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.auth.adapter.response.PermissionVO;
import org.icedamericanomall.auth.app.rbac.PermissionQueryManager;
import org.icedamericanomall.auth.domain.model.rbac.Permission;
import org.icedamericanomall.common.api.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限管理 — 返回权限列表 + 权限树（菜单/按钮/API 三级）。
 * 随 RBAC 归属授权域迁入（ADR-018）；读模型已强类型化。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/permissions")
@RequiredArgsConstructor
@PreAuthorize("@ss.hasPermi('user:admin')")
public class PermissionController {

    private final PermissionQueryManager permissionQueryManager;

    @GetMapping
    public Result<List<PermissionVO>> listPermissions() {
        return Result.ok(permissionQueryManager.listPermissions().stream()
                .map(PermissionVO::from).toList());
    }

    @GetMapping("/tree")
    public Result<List<PermissionVO>> listPermissionTree() {
        return Result.ok(permissionQueryManager.listPermissionTree().stream()
                .map(PermissionVO::from).toList());
    }
}
