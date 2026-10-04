package org.icedamericanomall.user.app.user;

import org.icedamericanomall.client.AuthContextClient;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.security.UserInfo;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.dto.TokenRevokeCO;
import org.icedamericanomall.user.app.oplog.OperationAuditConstants;
import org.icedamericanomall.user.app.oplog.OperationLogManager;
import org.icedamericanomall.user.domain.enums.UserStatusEnum;
import org.icedamericanomall.user.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 管理后台用户运营测试 —— 审计留痕（ADR-032 管道二）+ 禁用即下线（ADR-031）。
 *
 * <p>覆盖：启用/禁用与角色授予的「变更后留痕」顺序；非法状态在写库前即失败且不留痕；
 * 禁用触发 authorization-service 用户级令牌吊销、启用不触发（且吊销调用位于本地事务提交之后）。
 */
@DisplayName("UserAdminManager — 管理操作审计留痕 + 禁用即下线")
@ExtendWith(MockitoExtension.class)
class UserAdminManagerTest {

    private static final Long TARGET_USER_ID = 7L;
    private static final Long ADMIN_ID = 1001L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthContextClient authContextClient;

    @Mock
    private OperationLogManager operationLogManager;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private UserAdminManager userAdminManager;

    /** 桩：本地「状态变更 + 审计」事务回调同步执行（等价于事务成功提交）。 */
    @BeforeEach
    void stubTransactionTemplate() {
        lenient().doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @AfterEach
    void clearUserContext() {
        UserContext.removeUser();
    }

    @Test
    @DisplayName("禁用用户 — 先更新状态，再按 USER:UPDATE_USER_STATUS 留痕（含目标用户与目标状态）")
    void shouldRecordAuditAfterUpdateStatus_whenDisableUser() {
        UserContext.setUser(UserInfo.builder().userId(ADMIN_ID).build());

        userAdminManager.updateStatus(TARGET_USER_ID, 0);

        InOrder inOrder = inOrder(userRepository, operationLogManager);
        inOrder.verify(userRepository).updateStatusById(TARGET_USER_ID, UserStatusEnum.FROZEN);
        inOrder.verify(operationLogManager).record(ADMIN_ID, OperationAuditConstants.MODULE_USER,
                OperationAuditConstants.ACTION_UPDATE_USER_STATUS, "targetUserId=7, status=FROZEN");
    }

    @Test
    @DisplayName("启用用户 — 留痕摘要记目标状态 NORMAL")
    void shouldRecordNormalStatus_whenEnableUser() {
        UserContext.setUser(UserInfo.builder().userId(ADMIN_ID).build());

        userAdminManager.updateStatus(TARGET_USER_ID, 1);

        verify(operationLogManager).record(ADMIN_ID, OperationAuditConstants.MODULE_USER,
                OperationAuditConstants.ACTION_UPDATE_USER_STATUS, "targetUserId=7, status=NORMAL");
    }

    @Test
    @DisplayName("禁用用户 — 提交本地事务后触发用 authorization-service 用户级令牌吊销（ADR-031）")
    void shouldRevokeUserTokens_whenDisableUser() {
        UserContext.setUser(UserInfo.builder().userId(ADMIN_ID).build());

        userAdminManager.updateStatus(TARGET_USER_ID, 0);

        verify(authContextClient).revokeUserTokens(TokenRevokeCO.of(TARGET_USER_ID));
    }

    @Test
    @DisplayName("禁用用户 — 吊销（Feign）在本地事务提交之后调用，绝不在事务内调远端")
    void shouldRevokeAfterLocalTransaction_whenDisableUser() {
        UserContext.setUser(UserInfo.builder().userId(ADMIN_ID).build());

        userAdminManager.updateStatus(TARGET_USER_ID, 0);

        InOrder inOrder = inOrder(transactionTemplate, authContextClient);
        inOrder.verify(transactionTemplate).executeWithoutResult(any());
        inOrder.verify(authContextClient).revokeUserTokens(TokenRevokeCO.of(TARGET_USER_ID));
    }

    @Test
    @DisplayName("启用用户 — 不触发令牌吊销（禁用才需要下线）")
    void shouldNotRevokeUserTokens_whenEnableUser() {
        UserContext.setUser(UserInfo.builder().userId(ADMIN_ID).build());

        userAdminManager.updateStatus(TARGET_USER_ID, 1);

        verify(authContextClient, never()).revokeUserTokens(any());
    }

    @Test
    @DisplayName("非法状态 — 抛业务异常，且不改状态、不留痕、不触发吊销")
    void shouldSkipStateChangeAndAudit_whenStatusIllegal() {
        UserContext.setUser(UserInfo.builder().userId(ADMIN_ID).build());

        assertThatThrownBy(() -> userAdminManager.updateStatus(TARGET_USER_ID, 9))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(userRepository, operationLogManager, authContextClient, transactionTemplate);
    }

    @Test
    @DisplayName("修改角色 — 委托 authorization-service 后按 USER:SET_USER_ROLE 留痕")
    void shouldRecordAuditAfterDelegation_whenSetUserRole() {
        UserContext.setUser(UserInfo.builder().userId(ADMIN_ID).build());

        userAdminManager.setUserRole(TARGET_USER_ID, 2);

        InOrder inOrder = inOrder(authContextClient, operationLogManager);
        inOrder.verify(authContextClient).setUserRole(TARGET_USER_ID, 2);
        inOrder.verify(operationLogManager).record(ADMIN_ID, OperationAuditConstants.MODULE_USER,
                OperationAuditConstants.ACTION_SET_USER_ROLE, "targetUserId=7, roleType=2");
    }
}
