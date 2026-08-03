package org.icedamericanomall.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.mapper.RoleMapper;
import org.icedamericanomall.service.RoleManageService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleManageServiceImpl implements RoleManageService {

    private final RoleMapper roleMapper;

    @Override
    public List<Map<String, Object>> listAllRoles() {
        return roleMapper.selectAllRoles();
    }

    @Override
    public Map<String, Object> createRole(String name, String code, String description) {
        if (name == null || name.isBlank() || code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "角色名称和编码不能为空");
        }
        Map<String, Object> role = new HashMap<>();
        role.put("name", name);
        role.put("code", code);
        role.put("description", description);
        int rows = roleMapper.insertRole(role);
        if (rows <= 0) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "创建角色失败");
        log.info("角色创建成功: name={}, code={}", name, code);
        return role;
    }

    @Override
    public void updateRole(Long id, String name, String description) {
        Map<String, Object> role = new HashMap<>();
        role.put("id", id);
        role.put("name", name);
        role.put("description", description);
        int rows = roleMapper.updateRole(role);
        if (rows <= 0) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "角色不存在");
        log.info("角色更新成功: id={}", id);
    }

    @Override
    public void deleteRole(Long id) {
        int rows = roleMapper.deleteRole(id);
        if (rows <= 0) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "角色不存在或为系统内置角色不可删除");
        log.info("角色删除成功: id={}", id);
    }

    @Override
    public List<Long> getRolePermissions(Long roleId) {
        return roleMapper.selectPermissionIdsByRoleId(roleId);
    }

    @Override
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        if (permissionIds == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "permissionIds 不能为空");
        }
        roleMapper.deleteRolePermissions(roleId);
        for (Long permId : permissionIds) {
            roleMapper.insertRolePermission(roleId, permId);
        }
        log.info("角色权限分配成功: roleId={}, permCount={}", roleId, permissionIds.size());
    }
}
