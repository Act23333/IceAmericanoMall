package org.icedamericanomall.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UserContext} 权限便捷方法的语义测试 —— 核心是<b>与 {@code PermissionService} 对齐</b>。
 *
 * <h2>为什么要有这个文件（一个只会在大改时炸的隐性分叉）</h2>
 * 平台里存在<b>两条</b>权限判定路径：
 * <ul>
 *   <li>{@code @PreAuthorize("@ss.hasPermi(...)")} → {@link PermissionService}（认 {@code *:*:*} 通配）；</li>
 *   <li>控制器内 {@code UserContext.hasPermission(...)} → 本类（改造前<b>只做精确匹配</b>）。</li>
 * </ul>
 * 第二条路径存在的原因很具体：{@code spring-boot-starter-security} 在 {@code ia-common} 是
 * {@code optional}，<b>pay / merchant / ai / logistics 四个服务没有它</b>，{@code @PreAuthorize}
 * 根本不可用，于是退化成代码内判断（如 {@code AdminPayController#assertAdmin}）。
 *
 * <p>今天两条路径"恰好"一致，因为 {@code ROLE_ADMIN} 同时持有 {@code *:*:*} 与 {@code user:admin}。
 * 但只要按公司口径把管理员<b>收窄成只留 {@code *:*:*}</b>，那些服务的代码内判断就会
 * <b>开始拒绝管理员</b> —— 一个在权限矩阵大改时才会暴露的雷。故此处统一成同一语义。
 */
@DisplayName("UserContext 权限便捷方法 — 必须与 PermissionService 同语义（认 *:*:*）")
class UserContextPermissionSemanticsTest {

    private static final String WILDCARD = "*:*:*";

    @AfterEach
    void clear() {
        UserContext.removeUser();
    }

    @Test
    @DisplayName("无上下文 → 拒绝（fail-closed）")
    void shouldDeny_whenNoUser() {
        UserContext.removeUser();

        assertThat(UserContext.hasPermission("user:admin")).isFalse();
        assertThat(UserContext.hasAnyPermission("user:admin", "seller:admin")).isFalse();
    }

    @Test
    @DisplayName("★ 对齐回归 — 只持 *:*:* 也必须在代码内判定中通过（改造前会拒绝）")
    void shouldAllow_whenOnlyWildcard() {
        UserContext.setUser(UserInfo.builder().userId(1L).permissions(Set.of(WILDCARD)).build());

        assertThat(UserContext.hasPermission("user:admin"))
                .as("PermissionService 认通配符，这里也必须认 —— 否则「给管理员收窄成只留 *:*:*」时"
                        + "pay/merchant/ai/logistics 的 assertAdmin() 会把管理员自己挡掉")
                .isTrue();
        assertThat(UserContext.hasAnyPermission("invoice:issue", "user:admin")).isTrue();
    }

    @Test
    @DisplayName("精确匹配 — 未持有即拒绝（不能因为「有权限概念」就放行）")
    void shouldMatchExactly() {
        UserContext.setUser(UserInfo.builder()
                .userId(1L).roles(Set.of("ROLE_SELLER")).permissions(Set.of("seller:admin")).build());

        assertThat(UserContext.hasPermission("seller:admin")).isTrue();
        assertThat(UserContext.hasPermission("user:admin")).isFalse();
        assertThat(UserContext.hasAnyPermission("user:admin", "seller:admin")).isTrue();
        assertThat(UserContext.hasAnyPermission("user:admin", "invoice:issue")).isFalse();
    }

    @Test
    @DisplayName("★ Q6 对齐 — 代码内判定同样不得用通配符顶 rbac:admin")
    void shouldNotGrantRbacAdminThroughWildcard() {
        UserContext.setUser(UserInfo.builder().userId(1L).permissions(Set.of(WILDCARD)).build());

        assertThat(UserContext.hasPermission("rbac:admin"))
                .as("两条判定路径必须同语义 —— 否则「显式授予才能改 RBAC」只在一个入口成立")
                .isFalse();
        assertThat(UserContext.hasAnyPermission("rbac:admin")).isFalse();
        assertThat(UserContext.hasPermission("user:admin")).isTrue();
    }
}
