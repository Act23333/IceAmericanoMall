package org.icedamericanomall.auth.adapter.web.internal;

import org.icedamericanomall.auth.adapter.request.TokenRevokeReq;
import org.icedamericanomall.auth.app.AuthManager;
import org.icedamericanomall.auth.app.rbac.RoleManageManager;
import org.icedamericanomall.auth.app.rbac.SecurityContextManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

/**
 * 授权域内部端点单元测试（ADR-031）—— 用户级令牌吊销端点薄委托，无 {@code Result} 包装。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InternalAuthController — 用户级令牌吊销端点")
class InternalAuthControllerTest {

    @Mock
    private SecurityContextManager securityContextManager;

    @Mock
    private RoleManageManager roleManageManager;

    @Mock
    private AuthManager authManager;

    @InjectMocks
    private InternalAuthController controller;

    @Test
    @DisplayName("POST /internal/auth/token-version/revoke — 委托 AuthManager.revokeUserTokens(userId)，不包装 Result")
    void shouldDelegateToAuthManager_whenRevokeUserTokens() {
        TokenRevokeReq req = new TokenRevokeReq();
        req.setUserId(7L);

        controller.revokeUserTokens(req);

        verify(authManager).revokeUserTokens(7L);
    }
}
