package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.noLazy.common.domain.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 大厂标准: 权限管理 — 管理员查看/创建权限。
 */
@RestController
@RequestMapping("/api/admin/permissions")
@RequiredArgsConstructor
@PreAuthorize("@ss.hasPermi('user:admin')")
public class PermissionController {

    @GetMapping
    public Result<List<Map<String, Object>>> listPermissions() {
        // TODO: permissionMapper.selectAll() — 返回树形结构
        return Result.ok(Collections.emptyList());
    }

    @GetMapping("/tree")
    public Result<List<Map<String, Object>>> listPermissionTree() {
        // TODO: permissionMapper.selectAllAsTree() — 菜单/按钮/API 三级树
        return Result.ok(Collections.emptyList());
    }
}
