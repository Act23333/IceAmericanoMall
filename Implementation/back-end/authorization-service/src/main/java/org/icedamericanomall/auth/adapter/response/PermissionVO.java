package org.icedamericanomall.auth.adapter.response;

import lombok.Data;
import org.icedamericanomall.auth.domain.model.rbac.Permission;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 权限视图对象（admin 后台）—— 树形节点；JSON 键与迁出前 Map 读模型一致
 * （{@code parent_id} / {@code sort_order} 由 SNAKE_CASE 策略保持）。
 */
@Data
public class PermissionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String code;

    /** 类型：1=菜单, 2=按钮, 3=API接口 */
    private Integer type;

    private Long parentId;

    private String path;

    private Integer sortOrder;

    private Integer status;

    /** 子权限 */
    private List<PermissionVO> children = new ArrayList<>();

    /** 领域读模型 → 视图对象（递归组装子树）。 */
    public static PermissionVO from(Permission permission) {
        PermissionVO vo = new PermissionVO();
        vo.setId(permission.getId());
        vo.setName(permission.getName());
        vo.setCode(permission.getCode());
        vo.setType(permission.getType());
        vo.setParentId(permission.getParentId());
        vo.setPath(permission.getPath());
        vo.setSortOrder(permission.getSortOrder());
        vo.setStatus(permission.getStatus());
        for (Permission child : permission.getChildren()) {
            vo.getChildren().add(PermissionVO.from(child));
        }
        return vo;
    }
}
