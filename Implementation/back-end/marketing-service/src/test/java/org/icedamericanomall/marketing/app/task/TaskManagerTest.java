package org.icedamericanomall.marketing.app.task;

import org.icedamericanomall.dto.TaskCompleteCO;
import org.icedamericanomall.dto.TaskCompleteResultCO;
import org.icedamericanomall.marketing.adapter.response.DailyTaskVO;
import org.icedamericanomall.marketing.app.points.PointsManager;
import org.icedamericanomall.marketing.domain.model.task.TaskDef;
import org.icedamericanomall.marketing.domain.model.task.TaskRecord;
import org.icedamericanomall.marketing.domain.repository.TaskDefRepository;
import org.icedamericanomall.marketing.domain.repository.TaskRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TaskManager 每日任务编排单元测试 —— I49 幂等完成（首次/顺序重放/并发回落/停用/不存在）
 * + biz_date 服务端计算 + M14 任务列表完成状态映射（ADR-047）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskManager 每日任务编排单元测试")
class TaskManagerTest {

    private static final Long USER_ID = 1L;
    private static final String CODE_ORDER = "DAILY_ORDER";
    private static final String CODE_REVIEW = "DAILY_REVIEW";

    @Mock TaskDefRepository taskDefRepository;
    @Mock TaskRecordRepository taskRecordRepository;
    @Mock PointsManager pointsManager;

    private TaskManager taskManager;

    @BeforeEach
    void setUp() {
        taskManager = new TaskManager(taskDefRepository, taskRecordRepository, pointsManager);
    }

    @Test
    @DisplayName("completeTask — 首次完成：插记录+发积分同事务，返回 true+奖励值")
    void shouldCompleteAndAwardPoints_whenFirstCompletionToday() {
        when(taskDefRepository.findByCode(CODE_ORDER)).thenReturn(enabledDef(CODE_ORDER, 20));
        when(taskRecordRepository.findByUserAndCodeAndDate(eq(USER_ID), eq(CODE_ORDER), any()))
                .thenReturn(null);

        TaskCompleteResultCO result = taskManager.completeTask(new TaskCompleteCO(USER_ID, CODE_ORDER));

        assertTrue(result.isCompleted());
        assertEquals(20, result.getRewardPoints());
        ArgumentCaptor<TaskRecord> recordCaptor = ArgumentCaptor.forClass(TaskRecord.class);
        verify(taskRecordRepository).insert(recordCaptor.capture());
        verify(pointsManager).addPoints(USER_ID, 20, 3, "task:" + CODE_ORDER);
    }

    @Test
    @DisplayName("completeTask — biz_date 由服务端按当天计算（调用方无关，防伪造日期刷分）")
    void shouldComputeBizDateServerSide_whenCompleting() {
        when(taskDefRepository.findByCode(CODE_ORDER)).thenReturn(enabledDef(CODE_ORDER, 20));
        when(taskRecordRepository.findByUserAndCodeAndDate(eq(USER_ID), eq(CODE_ORDER), any()))
                .thenReturn(null);

        taskManager.completeTask(new TaskCompleteCO(USER_ID, CODE_ORDER));

        ArgumentCaptor<TaskRecord> recordCaptor = ArgumentCaptor.forClass(TaskRecord.class);
        verify(taskRecordRepository).insert(recordCaptor.capture());
        assertEquals(LocalDate.now(), recordCaptor.getValue().getBizDate());
    }

    @Test
    @DisplayName("completeTask — 顺序重放（当日已有记录）→ false+0，不再插记录/发积分")
    void shouldReturnNotCompleted_whenSequentialReplay() {
        when(taskDefRepository.findByCode(CODE_ORDER)).thenReturn(enabledDef(CODE_ORDER, 20));
        when(taskRecordRepository.findByUserAndCodeAndDate(eq(USER_ID), eq(CODE_ORDER), any()))
                .thenReturn(TaskRecord.record(USER_ID, CODE_ORDER, 20, LocalDate.now()));

        TaskCompleteResultCO result = taskManager.completeTask(new TaskCompleteCO(USER_ID, CODE_ORDER));

        assertFalse(result.isCompleted());
        assertEquals(0, result.getRewardPoints());
        verify(taskRecordRepository, never()).insert(any());
        verify(pointsManager, never()).addPoints(anyLong(), anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("completeTask — 并发撞 uk_user_task_date → DuplicateKey 回落 false+0，积分不发放")
    void shouldFallBackToNotCompleted_whenConcurrentDuplicateKey() {
        when(taskDefRepository.findByCode(CODE_ORDER)).thenReturn(enabledDef(CODE_ORDER, 20));
        when(taskRecordRepository.findByUserAndCodeAndDate(eq(USER_ID), eq(CODE_ORDER), any()))
                .thenReturn(null);
        doThrow(new DuplicateKeyException("uk_user_task_date"))
                .when(taskRecordRepository).insert(any());

        TaskCompleteResultCO result = taskManager.completeTask(new TaskCompleteCO(USER_ID, CODE_ORDER));

        assertFalse(result.isCompleted());
        assertEquals(0, result.getRewardPoints());
        verify(pointsManager, never()).addPoints(anyLong(), anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("completeTask — 任务不存在 → false+0，不触达记录仓储/积分")
    void shouldReturnNotCompleted_whenTaskDefMissing() {
        when(taskDefRepository.findByCode(CODE_ORDER)).thenReturn(null);

        TaskCompleteResultCO result = taskManager.completeTask(new TaskCompleteCO(USER_ID, CODE_ORDER));

        assertFalse(result.isCompleted());
        assertEquals(0, result.getRewardPoints());
        verifyNoInteractions(taskRecordRepository);
        verifyNoInteractions(pointsManager);
    }

    @Test
    @DisplayName("completeTask — 任务停用 → false+0")
    void shouldReturnNotCompleted_whenTaskDefDisabled() {
        TaskDef disabled = enabledDef(CODE_ORDER, 20);
        disabled.setStatus(0);
        when(taskDefRepository.findByCode(CODE_ORDER)).thenReturn(disabled);

        TaskCompleteResultCO result = taskManager.completeTask(new TaskCompleteCO(USER_ID, CODE_ORDER));

        assertFalse(result.isCompleted());
        assertEquals(0, result.getRewardPoints());
        verify(taskRecordRepository, never()).insert(any());
        verify(pointsManager, never()).addPoints(anyLong(), anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("dailyTasks — 全部启用任务映射完成状态（今日已完成的为 true）")
    void shouldMapCompletionState_whenListDailyTasks() {
        when(taskDefRepository.findAllEnabled())
                .thenReturn(List.of(enabledDef(CODE_ORDER, 20), enabledDef(CODE_REVIEW, 10)));
        when(taskRecordRepository.existsByUserAndCodeAndDate(USER_ID, CODE_ORDER, LocalDate.now()))
                .thenReturn(true);
        when(taskRecordRepository.existsByUserAndCodeAndDate(USER_ID, CODE_REVIEW, LocalDate.now()))
                .thenReturn(false);

        List<DailyTaskVO> tasks = taskManager.dailyTasks(USER_ID);

        assertEquals(2, tasks.size());
        DailyTaskVO order = tasks.get(0);
        assertEquals(CODE_ORDER, order.getCode());
        assertEquals(20, order.getRewardPoints());
        assertTrue(order.isCompleted());
        DailyTaskVO review = tasks.get(1);
        assertEquals(CODE_REVIEW, review.getCode());
        assertEquals(10, review.getRewardPoints());
        assertFalse(review.isCompleted());
    }

    // ==================== 测试脚手架 ====================

    /** 启用状态的任务定义（seed：DAILY_ORDER +20 / DAILY_REVIEW +10）。 */
    private TaskDef enabledDef(String code, int rewardPoints) {
        return TaskDef.builder()
                .id(1L)
                .code(code)
                .name("任务 " + code)
                .type(1)
                .rewardPoints(rewardPoints)
                .status(TaskDef.STATUS_ENABLED)
                .build();
    }
}
