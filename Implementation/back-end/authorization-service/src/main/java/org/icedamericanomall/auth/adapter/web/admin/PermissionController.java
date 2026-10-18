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
// 权限位拆分（5a 批 8，2026-10-18）：RBAC 管理整体挂 rbac:admin（Q11：**含读**）。
// 理由：读"谁能做什么"本身就是敏感信息；且 PermissionBits 的通配符对 rbac:admin 是**排除**的
// —— 它只能被种子显式授予，故这两个控制器是唯一真正的"超管专属面"。
@PreAuthorize("@ss.hasPermi('rbac:admin')")
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
