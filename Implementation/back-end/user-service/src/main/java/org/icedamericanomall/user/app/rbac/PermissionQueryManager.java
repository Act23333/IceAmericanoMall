package org.icedamericanomall.user.app.rbac;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.domain.repository.RoleRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限查询（应用层）—— 权限列表 + 权限树（菜单/按钮/API 三级，parent_id 关联）。
 *
 * <p>RBAC 五表模型: sys_permission.type: 1=菜单, 2=按钮, 3=API接口。
 * 树形组装属读模型塑形，从原 Controller 下沉到本层（Controller 只做参数解析 + Result 包装）。
 */
@Component
@RequiredArgsConstructor
public class PermissionQueryManager {

    private final RoleRepository roleRepository;

    /** 权限平铺列表。 */
    public List<Map<String, Object>> listPermissions() {
        return roleRepository.findAllPermissions();
    }

    /** 权限树（按 parent_id 组装 children）。 */
    public List<Map<String, Object>> listPermissionTree() {
        List<Map<String, Object>> all = roleRepository.findAllPermissions();

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
        return roots;
    }
}
