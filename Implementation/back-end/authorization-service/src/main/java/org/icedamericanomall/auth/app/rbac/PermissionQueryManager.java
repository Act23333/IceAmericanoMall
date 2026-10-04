package org.icedamericanomall.auth.app.rbac;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.auth.domain.model.rbac.Permission;
import org.icedamericanomall.auth.domain.repository.RoleRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限查询（应用层）—— 权限列表 + 权限树（菜单/按钮/API 三级，parent_id 关联）。
 * （归属自 user-service 迁入，ADR-018；读模型已强类型化）
 */
@Component
@RequiredArgsConstructor
public class PermissionQueryManager {

    private final RoleRepository roleRepository;

    /** 权限平铺列表。 */
    public List<Permission> listPermissions() {
        return roleRepository.findAllPermissions();
    }

    /** 权限树（按 parent_id 组装 children；parent 缺失的挂根）。 */
    public List<Permission> listPermissionTree() {
        List<Permission> all = roleRepository.findAllPermissions();

        Map<Long, Permission> idMap = new LinkedHashMap<>();
        List<Permission> roots = new ArrayList<>();

        for (Permission perm : all) {
            idMap.put(perm.getId(), perm);
        }

        for (Permission perm : all) {
            Permission parent = perm.getParentId() == null ? null : idMap.get(perm.getParentId());
            if (parent == null || parent == perm) {
                roots.add(perm);
            } else {
                parent.addChild(perm);
            }
        }
        return roots;
    }
}
