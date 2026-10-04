package org.icedamericanomall.user.adapter.web.admin;

import org.icedamericanomall.user.adapter.web.admin.AdminUserController;
import org.icedamericanomall.user.app.user.UserAdminManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

/**
 * 管理员用户接口单元测试 —— 验证角色授予经应用层 Manager 委托（Controller 不直连 Feign）。
 */
@DisplayName("管理员用户接口 — 角色授予经应用层")
@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    @Mock
    private UserAdminManager userAdminManager;

    @InjectMocks
    private AdminUserController controller;

    @Test
    @DisplayName("角色授予 — 控制器委托 UserAdminManager.setUserRole（不直接持有 AuthContextClient）")
    void shouldDelegateToManager_whenSetUserRole() {
        controller.setUserRole(100L, 2);

        verify(userAdminManager).setUserRole(100L, 2);
    }

    @Test
    @DisplayName("启用/禁用 — 控制器委托 UserAdminManager.updateStatus")
    void shouldDelegateToManager_whenUpdateStatus() {
        controller.updateUserStatus(100L, 0);

        verify(userAdminManager).updateStatus(100L, 0);
    }
}
