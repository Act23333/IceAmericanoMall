package org.icedamericanomall.auth.infrastructure.persistence.rbac;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 权限表映射对象（PO）—— {@code sys_permission} 表（菜单/按钮/API 三级，parent_id 关联）。
 * 仅存在于基础设施持久化层，不外泄。
 */
@Data
@TableName("sys_permission")
public class PermissionPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    private String name;

    private String code;

    /** 类型：1=菜单, 2=按钮, 3=API接口 */
    private Integer type;

    private Long parentId;

    private String path;

    private Integer sortOrder;

    private Integer status;
}
