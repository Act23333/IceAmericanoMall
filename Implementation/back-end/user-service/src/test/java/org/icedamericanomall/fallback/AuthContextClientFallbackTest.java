package org.icedamericanomall.fallback;

import org.icedamericanomall.client.AuthContextClient;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.TokenRevokeCO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 授权上下文 Feign 降级语义单元测试（ADR-018 / ADR-031）。
 *
 * <p>用户级令牌吊销（{@code revokeUserTokens}）降级必须 <b>fail-open</b>：
 * 认证域不可用时管理端禁用主流程不得被阻断；与之相对，角色授予（{@code setUserRole}）
 * 维持原有 fail-closed（授权变更不允许静默丢失）。
 */
@DisplayName("AuthContextClientFallback — 降级语义")
class AuthContextClientFallbackTest {

    private final AuthContextClient client =
            new AuthContextClientFallback().create(new IllegalStateException("authorization-service down"));

    @Test
    @DisplayName("用户级令牌吊销降级 — fail-open：不抛异常（禁用主流程不因吊销失败阻断，仅告警）")
    void shouldNotThrow_whenRevokeUserTokensDegraded() {
        assertDoesNotThrow(() -> client.revokeUserTokens(TokenRevokeCO.of(7L)));
    }

    @Test
    @DisplayName("用户级令牌吊销降级 — 请求体为 null 亦不抛异常")
    void shouldNotThrow_whenRevokeUserTokensWithNullBody() {
        assertDoesNotThrow(() -> client.revokeUserTokens(null));
    }

    @Test
    @DisplayName("角色授予降级 — 维持 fail-closed：抛业务异常（授权变更不得静默丢失）")
    void shouldThrow_whenSetUserRoleDegraded() {
        assertThatThrownBy(() -> client.setUserRole(7L, 2))
                .isInstanceOf(BizException.class);
    }
}
