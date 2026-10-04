package org.icedamericanomall.user.app.oplog;

import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.security.UserInfo;
import org.icedamericanomall.user.infrastructure.persistence.oplog.OperationLogMapper;
import org.icedamericanomall.user.infrastructure.persistence.oplog.OperationLogPO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * {@code operation_log} 写入测试 —— 锁定 user 域审计落库的列映射（ADR-032 管道二）。
 *
 * <p>不变量：{@code action} 为 {@code MODULE:ACTION}；{@code detail} 为操作摘要；
 * {@code username}/{@code ip} 取当前登录态（缺失留空）；{@code create_time} 交 DB 默认值。
 */
@DisplayName("OperationLogManager — user 域审计落库")
@ExtendWith(MockitoExtension.class)
class OperationLogManagerTest {

    private static final Long ADMIN_ID = 1001L;

    @Mock
    private OperationLogMapper operationLogMapper;

    @InjectMocks
    private OperationLogManager operationLogManager;

    @AfterEach
    void clearUserContext() {
        UserContext.removeUser();
    }

    @Test
    @DisplayName("落库 — action 记为 MODULE:ACTION，detail 记摘要，username/ip 取登录态，createTime 留空交 DB 默认值")
    void shouldInsertQualifiedActionAndOperatorInfo_whenRecord() {
        UserContext.setUser(UserInfo.builder()
                .userId(ADMIN_ID).username("admin").clientIp("203.0.113.9").build());

        operationLogManager.record(ADMIN_ID, OperationAuditConstants.MODULE_USER,
                OperationAuditConstants.ACTION_UPDATE_USER_STATUS, "targetUserId=7, status=FROZEN");

        OperationLogPO inserted = capturedInsert();
        assertThat(inserted.getUserId()).isEqualTo(ADMIN_ID);
        assertThat(inserted.getUsername()).isEqualTo("admin");
        assertThat(inserted.getIp()).isEqualTo("203.0.113.9");
        assertThat(inserted.getAction()).isEqualTo("USER:UPDATE_USER_STATUS");
        assertThat(inserted.getDetail()).isEqualTo("targetUserId=7, status=FROZEN");
        assertThat(inserted.getCreateTime()).isNull();
    }

    @Test
    @DisplayName("落库 — 无登录态（系统/匿名）时 userId/username/ip 留空，不抛异常")
    void shouldInsertWithoutOperatorInfo_whenNoUserContext() {
        operationLogManager.record(null, OperationAuditConstants.MODULE_USER,
                OperationAuditConstants.ACTION_SET_USER_ROLE, "targetUserId=7, roleType=2");

        OperationLogPO inserted = capturedInsert();
        assertThat(inserted.getUserId()).isNull();
        assertThat(inserted.getUsername()).isNull();
        assertThat(inserted.getIp()).isNull();
        assertThat(inserted.getAction()).isEqualTo("USER:SET_USER_ROLE");
    }

    @Test
    @DisplayName("常量 — 模块/动作取值稳定（台账口径与 ADR-032 一致，避免魔法值漂移）")
    void shouldKeepStableAuditVocabulary() {
        assertThat(OperationAuditConstants.MODULE_USER).isEqualTo("USER");
        assertThat(OperationAuditConstants.ACTION_UPDATE_USER_STATUS).isEqualTo("UPDATE_USER_STATUS");
        assertThat(OperationAuditConstants.ACTION_SET_USER_ROLE).isEqualTo("SET_USER_ROLE");
    }

    /** 捕获本次插入的持久化对象。 */
    private OperationLogPO capturedInsert() {
        ArgumentCaptor<OperationLogPO> captor = ArgumentCaptor.forClass(OperationLogPO.class);
        verify(operationLogMapper).insert(captor.capture());
        return captor.getValue();
    }
}
