package org.icedamericanomall.service;

import java.util.List;
import java.util.Map;

/** RBAC 角色管理领域服务接口 */
public interface RoleManageService {

    List<Map<String, Object>> listAllRoles();

    Map<String, Object> createRole(String name, String code, String description);

    void updateRole(Long id, String name, String description);

    void deleteRole(Long id);

    List<Long> getRolePermissions(Long roleId);

    void assignPermissions(Long roleId, List<Long> permissionIds);
}
