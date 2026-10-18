package org.icedamericanomall.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权限判定语义测试（RBAC0 的唯一判定入口）—— 锁死三条规则 + <b>一条被移除的旁路</b>。
 *
 * <h2>为什么必须有一条「ROLE_ADMIN 不再放行」的回归用例</h2>
 * 2026-10-18 之前 {@code hasPermi} 里有「roles 含 ROLE_ADMIN 即放行」的短路。它有真实后果：
 * <b>权限位对管理员完全失效</b> —— 把管理员的权限全删光也照样通过一切 {@code @PreAuthorize}，
 * 于是「管理员也不能做 X」无法表达，权限拆分对管理员也永远无效。
 *
 * <p>移除它的前提是：{@code ROLE_ADMIN} 在种子里持 {@code *:*:*}，管理员经<b>通配符</b>全通。
 * 所以本测试同时钉两件事：<b>带通配符照旧全通</b>（行为不变）、<b>不带通配符则拒绝</b>（旁路确实没了）。
 * 少了后者，这次改动就等于没做 —— 有人把短路加回来也不会红。
 *
 * <p><b>一条不测的分支（刻意说明）</b>：{@code hasPermi} 里 {@code user.permissions() == null}
 * 的判定是<b>防御性</b>的 —— {@link UserInfo} 的构造器保证 {@code permissions} 非空
 * （实测：传 {@code null} 直接 NPE），所以「权限集合为 null」这个状态在本仓构造不出来。
 * 故不为它硬凑一条用例（用 Mockito 造 record 只为测一个不可达状态，收益低于维护成本）。
 */
@DisplayName("PermissionService — fail-closed / 通配符 / 精确匹配（ROLE_ADMIN 不再短路）")
class PermissionServiceTest {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String WILDCARD = "*:*:*";

    private final PermissionService permissionService = new PermissionService();

    @AfterEach
    void clearUserContext() {
        UserContext.removeUser();
    }

    @Test
    @DisplayName("无用户上下文 → 拒绝（fail-closed，不是放行）")
    void shouldDeny_whenNoUserContext() {
        UserContext.removeUser();

        assertThat(permissionService.hasPermi("user:admin")).isFalse();
        assertThat(permissionService.hasAnyPermi("user:admin", "seller:admin")).isFalse();
        assertThat(permissionService.hasAllPermi("user:admin")).isFalse();
    }

    @Test
    @DisplayName("★ 通配符 → 任意权限串都通过（超级管理员的唯一载体）")
    void shouldAllowEverything_whenWildcardPresent() {
        UserContext.setUser(UserInfo.builder()
                .userId(1L).roles(Set.of(ROLE_ADMIN)).permissions(Set.of(WILDCARD)).build());

        assertThat(permissionService.hasPermi("user:admin")).isTrue();
        assertThat(permissionService.hasPermi("invoice:issue")).isTrue();
        assertThat(permissionService.hasPermi("any:future:bit")).isTrue();
    }

    @Test
    @DisplayName("★ 缺陷回归 — 仅持 ROLE_ADMIN、无通配符 → 必须拒绝（短路已移除，禁止加回）")
    void shouldDeny_whenOnlyRoleAdminWithoutWildcard() {
        UserContext.setUser(UserInfo.builder()
                .userId(1L).roles(Set.of(ROLE_ADMIN)).permissions(Set.of("order:read")).build());

        assertThat(permissionService.hasPermi("user:admin"))
                .as("roles 含 ROLE_ADMIN 不再是放行理由 —— 否则权限位对管理员永失效，"
                        + "「管理员也不能做 X」无法表达（本次移除该短路的全部意义）")
                .isFalse();
        // 但他确实持有的位仍然通过（证明拒绝来自「没有这个位」，不是「一律拒绝」）
        assertThat(permissionService.hasPermi("order:read")).isTrue();
    }

    @Test
    @DisplayName("精确匹配 → 持有即通过；未持有即拒绝")
    void shouldMatchExactly() {
        UserContext.setUser(UserInfo.builder()
                .userId(1L).roles(Set.of("ROLE_SELLER")).permissions(Set.of("seller:admin", "order:read")).build());

        assertThat(permissionService.hasPermi("seller:admin")).isTrue();
        assertThat(permissionService.hasPermi("user:admin")).isFalse();
    }

    @Test
    @DisplayName("hasAnyPermi — 任一命中即通过；hasAllPermi — 必须全部命中")
    void shouldRespectAnyAndAllSemantics() {
        UserContext.setUser(UserInfo.builder()
                .userId(1L).roles(Set.of("ROLE_SELLER")).permissions(Set.of("seller:admin")).build());

        assertThat(permissionService.hasAnyPermi("user:admin", "seller:admin")).isTrue();
        assertThat(permissionService.hasAllPermi("seller:admin", "user:admin")).isFalse();
        assertThat(permissionService.hasAllPermi("seller:admin")).isTrue();
    }

    @Test
    @DisplayName("hasAnyPermi / hasAllPermi 传空数组 → 拒绝（不因「没有要求」而放行）")
    void shouldDeny_whenPermissionArgsEmpty() {
        UserContext.setUser(UserInfo.builder()
                .userId(1L).roles(Set.of(ROLE_ADMIN)).permissions(Set.of(WILDCARD)).build());

        assertThat(permissionService.hasAnyPermi()).isFalse();
        assertThat(permissionService.hasAllPermi()).isFalse();
    }

    @Test
    @DisplayName("hasRole — 只认角色，不受权限位影响（角色判定与权限判定是两件事）")
    void shouldJudgeRoleIndependently() {
        UserContext.setUser(UserInfo.builder()
                .userId(1L).roles(Set.of(ROLE_ADMIN)).permissions(Set.of()).build());

        assertThat(permissionService.hasRole(ROLE_ADMIN)).isTrue();
        assertThat(permissionService.hasRole("ROLE_SELLER")).isFalse();
    }
}
