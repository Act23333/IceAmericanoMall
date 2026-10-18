package org.icedamericanomall.user.app.user;

import org.icedamericanomall.client.AuthContextClient;
import org.icedamericanomall.common.api.ErrorInfo;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.TokenRevokeCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.user.app.oplog.OperationAuditConstants;
import org.icedamericanomall.user.app.oplog.OperationLogManager;
import org.icedamericanomall.user.domain.enums.UserStatusEnum;
import org.icedamericanomall.user.domain.model.oplog.OperationLog;
import org.icedamericanomall.user.domain.model.user.User;
import org.icedamericanomall.user.domain.repository.OperationLogRepository;
import org.icedamericanomall.user.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 令牌吊销对账补偿测试（U17 第二步）—— 锁死<b>两个不写就出真 bug 的条件</b>。
 *
 * <ol>
 *   <li><b>4xx 决不进对账</b>：{@code CLIENT} 是确定性失败，重放必然重复失败 ⇒ 必须写
 *       {@code permanent-4xx} 终结；否则每一轮都会重复同一次必然失败的调用（死循环）。</li>
 *   <li><b>已重新启用必须跳过</b>：禁用 → 吊销失败 → 管理员又启用（或用户已删除）⇒ 重放会把
 *       <b>正常用户的登录态踢掉</b>。故重放前重新读当前状态，非 FROZEN 一律写
 *       {@code skipped} 终结。</li>
 * </ol>
 *
 * <p>这两条按「判定条件」而非「实现细节」写：断言的是<b>有没有写终结记录 / 有没有调远端</b>，
 * 不是日志文案。日志随时会改，判定条件不能。
 */
@DisplayName("TokenRevokeReconcileManager — 对账补偿（4xx 不重放 / 已重新启用不踢人）")
@ExtendWith(MockitoExtension.class)
class TokenRevokeReconcileManagerTest {

    private static final Long USER_ID = 7L;
    private static final String TARGET = "7";

    /** 台账 action 全值（写入侧唯一入口组装，测试里也用它，避免手拼分隔符）。 */
    private static final String DISABLE_ACTION =
            OperationAuditConstants.actionKey(OperationAuditConstants.MODULE_USER,
                    OperationAuditConstants.ACTION_DISABLE_USER);

    private static final String REVOKE_ACTION =
            OperationAuditConstants.actionKey(OperationAuditConstants.MODULE_USER,
                    OperationAuditConstants.ACTION_REVOKE_USER_TOKENS);

    @Mock
    private OperationLogRepository operationLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthContextClient authContextClient;

    @Mock
    private OperationLogManager operationLogManager;

    @InjectMocks
    private TokenRevokeReconcileManager manager;

    @Test
    @DisplayName("★ 条件② — 已重新启用：跳过且不调吊销（否则踢掉正常用户）")
    void shouldSkipAndRecord_whenUserReEnabled() {
        givenDisableCandidate();
        when(operationLogRepository.existsByActionAndTargetSince(
                eq(REVOKE_ACTION), eq(TARGET), ArgumentMatchers.any(LocalDateTime.class))).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(UserStatusEnum.NORMAL)));

        int replayed = manager.reconcile();

        assertThat(replayed).isZero();
        verify(authContextClient, never()).revokeUserTokens(any());
        verify(operationLogManager).record(any(), eq(OperationAuditConstants.MODULE_USER),
                eq(OperationAuditConstants.ACTION_REVOKE_USER_TOKENS), eq(TARGET),
                eq("skipped: re-enabled or missing"));
    }

    @Test
    @DisplayName("★ 条件① — 吊销抛 4xx：写 permanent-4xx 终结，本轮不再重试")
    void shouldRecordPermanent_whenRevokeRejectedAsClientError() {
        givenDisableCandidate();
        when(operationLogRepository.existsByActionAndTargetSince(
                eq(REVOKE_ACTION), eq(TARGET), ArgumentMatchers.any(LocalDateTime.class))).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(UserStatusEnum.FROZEN)));
        doThrow(clientError()).when(authContextClient).revokeUserTokens(any());

        assertThat(manager.reconcile()).isZero();

        verify(operationLogManager).record(any(), eq(OperationAuditConstants.MODULE_USER),
                eq(OperationAuditConstants.ACTION_REVOKE_USER_TOKENS), eq(TARGET), eq("permanent-4xx"));
    }

    @Test
    @DisplayName("条件②反向 — 仍为 FROZEN：重放吊销并写 ok 终结")
    void shouldReplayAndRecordOk_whenStillFrozen() {
        givenDisableCandidate();
        when(operationLogRepository.existsByActionAndTargetSince(
                eq(REVOKE_ACTION), eq(TARGET), ArgumentMatchers.any(LocalDateTime.class))).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(UserStatusEnum.FROZEN)));

        assertThat(manager.reconcile()).isEqualTo(1);

        verify(authContextClient).revokeUserTokens(TokenRevokeCO.of(USER_ID));
        verify(operationLogManager).record(any(), eq(OperationAuditConstants.MODULE_USER),
                eq(OperationAuditConstants.ACTION_REVOKE_USER_TOKENS), eq(TARGET), eq("ok"));
    }

    @Test
    @DisplayName("不可用类别：不写终结记录 ⇒ 下一轮继续重放（这正是「可收敛」的定义）")
    void shouldNotRecordTerminal_whenRevokeUnavailable() {
        givenDisableCandidate();
        when(operationLogRepository.existsByActionAndTargetSince(
                eq(REVOKE_ACTION), eq(TARGET), ArgumentMatchers.any(LocalDateTime.class))).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(UserStatusEnum.FROZEN)));
        doThrow(new BizException(ErrorCode.SYSTEM_BUSY)).when(authContextClient).revokeUserTokens(any());

        assertThat(manager.reconcile()).isZero();

        verify(operationLogManager, never()).record(any(), any(),
                eq(OperationAuditConstants.ACTION_REVOKE_USER_TOKENS), any(), any());
    }

    @Test
    @DisplayName("已有终结记录：整条跳过（不读用户、不调远端）")
    void shouldSkipEntirely_whenAlreadyTerminal() {
        LocalDateTime disableTime = givenDisableCandidate();
        when(operationLogRepository.existsByActionAndTargetSince(REVOKE_ACTION, TARGET, disableTime))
                .thenReturn(true);

        assertThat(manager.reconcile()).isZero();

        verify(userRepository, never()).findById(any());
        verify(authContextClient, never()).revokeUserTokens(any());
    }

    // ==================== 测试脚手架 ====================

    /** 造一条窗口内的「禁用」候选记录，返回它的创建时间（终结判定要用同一条时间线）。 */
    private LocalDateTime givenDisableCandidate() {
        LocalDateTime disableTime = LocalDateTime.now().minusMinutes(5);
        when(operationLogRepository.findByActionSince(eq(DISABLE_ACTION),
                ArgumentMatchers.any(LocalDateTime.class), ArgumentMatchers.anyInt()))
                .thenReturn(List.of(OperationLog.builder()
                        .action(DISABLE_ACTION)
                        .target(TARGET)
                        .detail("targetUserId=7, status=FROZEN")
                        .createTime(disableTime)
                        .build()));
        return disableTime;
    }

    private static User user(UserStatusEnum status) {
        return User.builder().id(USER_ID).status(status).build();
    }

    private static UpstreamException clientError() {
        return new UpstreamException(ErrorInfo.of(1103, "用户不存在", ErrorCategory.CLIENT, null), 404);
    }
}
