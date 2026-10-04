package org.icedamericanomall.auth.domain.model.rbac;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色（领域读模型）—— 后台角色管理/列表的领域形态。
 *
 * <p>角色是授权数据（ADR-018），归授权域；不是"账户类型"，
 * 一个账户可同时持有多个角色（如商家兼管理员）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    private Long id;

    private String name;

    private String code;

    private String description;

    /** 状态：1=启用 */
    private Integer status;

    /** 内置角色（1=系统内置，禁止删除） */
    private Boolean isSystem;
}
