package org.icedamericanomall.auth.adapter.response;

import lombok.Data;
import org.icedamericanomall.auth.domain.model.rbac.Role;

import java.io.Serial;
import java.io.Serializable;

/**
 * 角色视图对象（admin 后台）—— JSON 键与迁出前 Map 读模型一致
 * （{@code is_system} 等 snake_case 键由 SNAKE_CASE 策略 + 字段命名保持）。
 */
@Data
public class RoleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String code;

    private String description;

    /** 状态：1=启用 */
    private Integer status;

    /** 内置角色（1=系统内置，禁止删除） */
    private Integer isSystem;

    /** 领域读模型 → 视图对象。 */
    public static RoleVO from(Role role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setName(role.getName());
        vo.setCode(role.getCode());
        vo.setDescription(role.getDescription());
        vo.setStatus(role.getStatus());
        vo.setIsSystem(Boolean.TRUE.equals(role.getIsSystem()) ? 1 : 0);
        return vo;
    }
}
