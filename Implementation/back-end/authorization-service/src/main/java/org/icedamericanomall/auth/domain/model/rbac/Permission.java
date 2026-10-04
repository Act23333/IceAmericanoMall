package org.icedamericanomall.auth.domain.model.rbac;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限（领域读模型）—— 菜单/按钮/API 三级，parent_id 组成树（读模型塑形）。
 */
@Data
public class Permission {

    private Long id;

    private String name;

    private String code;

    /** 类型：1=菜单, 2=按钮, 3=API接口 */
    private Integer type;

    private Long parentId;

    private String path;

    private Integer sortOrder;

    private Integer status;

    /** 子权限（树形组装后填充；叶子为空列表） */
    private List<Permission> children = new ArrayList<>();

    public void addChild(Permission child) {
        this.children.add(child);
    }
}
