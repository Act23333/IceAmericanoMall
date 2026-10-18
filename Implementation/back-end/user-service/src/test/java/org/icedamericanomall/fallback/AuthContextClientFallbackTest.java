package org.icedamericanomall.fallback;

import org.icedamericanomall.client.AuthContextClient;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.TokenRevokeCO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 授权上下文 Feign 降级语义单元测试（ADR-018 / ADR-031）。
 *
 * <p><b>U17 第二步改判</b>：用户级令牌吊销（{@code revokeUserTokens}）由 fail-open 改为<b>抛</b> ——
 * 静默失败会让「账号已被禁用」与「旧令牌仍可登录」长期并存且无人感知。主流程仍不被阻断，
 * 因为调用点 {@code UserAdminManager#updateStatus} 已加防护（4xx 登记终结 / 不可用留待对账重放）。
 * 角色授予（{@code setUserRole}）维持 fail-closed（授权变更不允许静默丢失）。
 */
@DisplayName("AuthContextClientFallback — 降级语义")
class AuthContextClientFallbackTest {

    private final AuthContextClient client =
            new AuthContextClientFallback().create(new IllegalStateException("authorization-service down"));

    @Test
    @DisplayName("★ 用户级令牌吊销降级 — U17 起改抛 SYSTEM_BUSY（不再静默：静默=账号已禁用但旧令牌仍可登录）")
    void shouldThrowSystemBusy_whenRevokeUserTokensUnavailable() {
        assertThatThrownBy(() -> client.revokeUserTokens(TokenRevokeCO.of(7L)))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("用户级令牌吊销降级 — 请求体为 null 亦抛（不因缺参变成静默成功）")
    void shouldThrowSystemBusy_whenRevokeUserTokensWithNullBody() {
        assertThatThrownBy(() -> client.revokeUserTokens(null))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("角色授予降级 — 维持 fail-closed：抛业务异常（授权变更不得静默丢失）")
    void shouldThrow_whenSetUserRoleDegraded() {
        assertThatThrownBy(() -> client.setUserRole(7L, 2))
                .isInstanceOf(BizException.class);
    }
}
