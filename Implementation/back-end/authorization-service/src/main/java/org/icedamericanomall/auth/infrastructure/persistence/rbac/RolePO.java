package org.icedamericanomall.auth.infrastructure.persistence.rbac;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色表映射对象（PO）—— {@code sys_role} 表（ADR-018）。
 * 仅存在于基础设施持久化层，不外泄；由仓储层转换为领域读模型。
 */
@Data
@TableName("sys_role")
public class RolePO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String code;

    private String description;

    /** 状态：1=启用 */
    private Integer status;

    /** 内置角色（1=系统内置，禁止删除） */
    private Integer isSystem;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
