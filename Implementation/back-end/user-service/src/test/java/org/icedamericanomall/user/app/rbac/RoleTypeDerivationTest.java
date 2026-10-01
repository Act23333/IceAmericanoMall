package org.icedamericanomall.user.app.rbac;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * roleType 派生口径单元测试（V5.2）—— 五表角色编码 → 兼容字段 roleType。
 */
@DisplayName("RoleTypeDerivation 单元测试")
class RoleTypeDerivationTest {

    @Test
    @DisplayName("ROLE_ADMIN → 2（管理员优先于商家）")
    void shouldDeriveAdmin() {
        assertEquals(RoleTypeDerivation.ROLE_TYPE_ADMIN, RoleTypeDerivation.fromRoleCodes(Set.of("ROLE_ADMIN")));
        assertEquals(RoleTypeDerivation.ROLE_TYPE_ADMIN,
                RoleTypeDerivation.fromRoleCodes(Set.of("ROLE_USER", "ROLE_SELLER", "ROLE_ADMIN")));
    }

    @Test
    @DisplayName("ROLE_SELLER → 1（商家保留 ROLE_USER 时仍为商家）")
    void shouldDeriveSeller() {
        assertEquals(RoleTypeDerivation.ROLE_TYPE_SELLER,
                RoleTypeDerivation.fromRoleCodes(Set.of("ROLE_USER", "ROLE_SELLER")));
    }

    @Test
    @DisplayName("仅 ROLE_USER / 空集合 / null → 0（普通用户）")
    void shouldDeriveUser() {
        assertEquals(RoleTypeDerivation.ROLE_TYPE_USER, RoleTypeDerivation.fromRoleCodes(Set.of("ROLE_USER")));
        assertEquals(RoleTypeDerivation.ROLE_TYPE_USER, RoleTypeDerivation.fromRoleCodes(Set.of()));
        assertEquals(RoleTypeDerivation.ROLE_TYPE_USER, RoleTypeDerivation.fromRoleCodes(null));
    }
}
