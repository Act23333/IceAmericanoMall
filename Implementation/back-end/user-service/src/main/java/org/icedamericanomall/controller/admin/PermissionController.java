package org.icedamericanomall.controller.admin;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.mapper.PermissionMapper;
import org.noLazy.common.domain.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 大厂标准: 权限管理 — 返回权限列表 + 权限树（菜单/按钮/API 三级）。
 *
 * RBAC 五表模型: sys_permission.type: 1=菜单, 2=按钮, 3=API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/permissions")
@PreAuthorize("@ss.hasPermi('user:admin')")
public class PermissionController {

    private final PermissionMapper permissionMapper;

    public PermissionController(PermissionMapper permissionMapper) {
        this.permissionMapper = permissionMapper;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> listPermissions() {
        List<Map<String, Object>> permissions = permissionMapper.selectAllPermissions();
        return Result.ok(permissions);
    }

    @GetMapping("/tree")
    public Result<List<Map<String, Object>>> listPermissionTree() {
        List<Map<String, Object>> all = permissionMapper.selectAllPermissions();

        // 构建树形结构 (parent_id 关联)
        Map<Long, Map<String, Object>> idMap = new LinkedHashMap<>();
        List<Map<String, Object>> roots = new ArrayList<>();

        for (Map<String, Object> perm : all) {
            Long id = ((Number) perm.get("id")).longValue();
            Map<String, Object> node = new LinkedHashMap<>(perm);
            node.put("children", new ArrayList<>());
            idMap.put(id, node);
        }

        for (Map<String, Object> perm : all) {
            Long id = ((Number) perm.get("id")).longValue();
            Object parentIdObj = perm.get("parent_id");
            if (parentIdObj != null) {
                Long parentId = ((Number) parentIdObj).longValue();
                Map<String, Object> parent = idMap.get(parentId);
                if (parent != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("children");
                    children.add(idMap.get(id));
                } else {
                    roots.add(idMap.get(id));
                }
            } else {
                roots.add(idMap.get(id));
            }
        }

        return Result.ok(roots);
    }
}
