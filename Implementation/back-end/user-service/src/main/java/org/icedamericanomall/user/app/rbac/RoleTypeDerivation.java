package org.icedamericanomall.user.app.rbac;

import java.util.Set;

/**
 * {@code roleType} 派生口径（V5.2 / ADR-017）—— 对外响应中的兼容字段。
 *
 * <p>背景：{@code user.role_type} 列已退役（代码不读不写，DDL 注释 deprecated），
 * 角色/权限的唯一权威口径是 RBAC 五表（sys_role / sys_user_role / …）。为保持
 * {@code /api/user/**} 报文兼容，响应中的 {@code roleType} 由五表角色编码<b>派生</b>：
 * <ul>
 *   <li>含 {@code ROLE_ADMIN} → 2（管理员）</li>
 *   <li>含 {@code ROLE_SELLER} → 1（商家）</li>
 *   <li>其余 → 0（普通用户）</li>
 * </ul>
 */
public final class RoleTypeDerivation {

    /** 角色类型：普通用户 */
    public static final int ROLE_TYPE_USER = 0;

    /** 角色类型：商家 */
    public static final int ROLE_TYPE_SELLER = 1;

    /** 角色类型：管理员 */
    public static final int ROLE_TYPE_ADMIN = 2;

    private static final String CODE_ADMIN = "ROLE_ADMIN";
    private static final String CODE_SELLER = "ROLE_SELLER";

    private RoleTypeDerivation() {
    }

    /**
     * 由角色编码集合派生 roleType（null / 空集合 → 普通用户）。
     *
     * @param roleCodes RBAC 角色编码集合（五表口径）
     * @return 0=普通用户，1=商家，2=管理员
     */
    public static Integer fromRoleCodes(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return ROLE_TYPE_USER;
        }
        if (roleCodes.contains(CODE_ADMIN)) {
            return ROLE_TYPE_ADMIN;
        }
        if (roleCodes.contains(CODE_SELLER)) {
            return ROLE_TYPE_SELLER;
        }
        return ROLE_TYPE_USER;
    }
}
