package org.noLazy.common.enums;

import lombok.Getter;

/**
 * 系统角色枚举 — RBAC 角色定义。
 * <p>
 * 与 sys_role 表 code 字段对应，用于代码中类型安全的角色判断。
 */
@Getter
public enum RoleEnum {

    USER(0, "ROLE_USER", "普通用户"),
    VIP(1, "ROLE_VIP", "VIP用户"),
    SELLER(2, "ROLE_SELLER", "入驻商家"),
    ADMIN(3, "ROLE_ADMIN", "管理员");

    private final int roleType;
    private final String code;
    private final String desc;

    RoleEnum(int roleType, String code, String desc) {
        this.roleType = roleType;
        this.code = code;
        this.desc = desc;
    }

    /** 检查当前角色是否有足够权限（数值越大权限越高） */
    public boolean hasPrivilege(RoleEnum required) {
        return this.roleType >= required.roleType;
    }
}
