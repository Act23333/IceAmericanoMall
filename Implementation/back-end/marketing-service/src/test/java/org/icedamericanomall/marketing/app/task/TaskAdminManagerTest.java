package org.icedamericanomall.marketing.app.task;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.adapter.request.TaskCreateReq;
import org.icedamericanomall.marketing.adapter.request.TaskUpdateReq;
import org.icedamericanomall.marketing.adapter.response.TaskDefVO;
import org.icedamericanomall.marketing.domain.model.task.TaskDef;
import org.icedamericanomall.marketing.domain.repository.TaskDefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TaskAdminManager 管理端编排单元测试 —— AD52 创建（缺省归一 / code 唯一双路径 400 /
 * Phase 1 类型守卫）+ AD53 编辑（null-safe / 1103 / 停用即 I49 口径失效）+ AD51 分页透传
 * （V5.13 任务运营闭环，ADR-047）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskAdminManager 管理端编排单元测试")
class TaskAdminManagerTest {

    private static final String CODE_ORDER = "DAILY_ORDER";
    private static final LocalDateTime CREATE_TIME = LocalDateTime.of(2026, 10, 1, 8, 0);

    @Mock
    private TaskDefRepository taskDefRepository;

    private TaskAdminManager taskAdminManager;

    @BeforeEach
    void setUp() {
        taskAdminManager = new TaskAdminManager(taskDefRepository);
    }

    // ==================== AD52 创建 ====================

    @Test
    @DisplayName("create — 合法请求（type/status 缺省）→ 归一 type=1 status=1，code/name/rewardPoints 透传")
    void shouldSaveWithDefaults_whenCreateWithoutOptionalFields() {
        TaskCreateReq req = createReq(CODE_ORDER, 20);
        req.setType(null);
        req.setStatus(null);
        when(taskDefRepository.findByCode(CODE_ORDER)).thenReturn(null);

        taskAdminManager.create(req);

        ArgumentCaptor<TaskDef> captor = ArgumentCaptor.forClass(TaskDef.class);
        verify(taskDefRepository).save(captor.capture());
        TaskDef saved = captor.getValue();
        assertEquals(CODE_ORDER, saved.getCode());
        assertEquals("每日下单任务", saved.getName());
        assertEquals(20, saved.getRewardPoints());
        assertEquals(TaskDef.TYPE_DAILY, saved.getType());
        assertEquals(TaskDef.STATUS_ENABLED, saved.getStatus());
        assertTrue(saved.isEnabled());
    }

    @Test
    @DisplayName("create — code 已存在（先查命中）→ 400 PARAM_ERROR，不触发插入")
    void shouldThrow400_whenCodeExistsPreCheck() {
        when(taskDefRepository.findByCode(CODE_ORDER)).thenReturn(def(CODE_ORDER, 20));

        BizException ex = assertThrows(BizException.class,
                () -> taskAdminManager.create(createReq(CODE_ORDER, 20)));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(taskDefRepository, never()).save(any());
    }

    @Test
    @DisplayName("create — 先查未中但插入撞 uk_code（并发竞态）→ 回落 400 PARAM_ERROR")
    void shouldFallBackTo400_whenDuplicateKeyOnInsert() {
        when(taskDefRepository.findByCode(CODE_ORDER)).thenReturn(null);
        doThrow(new DuplicateKeyException("uk_code"))
                .when(taskDefRepository).save(any());

        BizException ex = assertThrows(BizException.class,
                () -> taskAdminManager.create(createReq(CODE_ORDER, 20)));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("create — type=2（Phase 1 仅每日，Phase 2 扩展位）→ 400 PARAM_ERROR，不落库")
    void shouldRejectNonDailyType_whenCreate() {
        when(taskDefRepository.findByCode(CODE_ORDER)).thenReturn(null);
        TaskCreateReq req = createReq(CODE_ORDER, 20);
        req.setType(2);

        BizException ex = assertThrows(BizException.class, () -> taskAdminManager.create(req));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(taskDefRepository, never()).save(any());
    }

    // ==================== AD53 编辑 ====================

    @Test
    @DisplayName("update — null-safe：仅传 name → 其余字段保持原值（聚合全量携带落库）")
    void shouldUpdateOnlyProvidedFields_whenUpdateNullSafe() {
        TaskDef existing = def(CODE_ORDER, 20);
        when(taskDefRepository.findByCode(CODE_ORDER)).thenReturn(existing);
        TaskUpdateReq req = new TaskUpdateReq();
        req.setName("新任务名");

        taskAdminManager.update(CODE_ORDER, req);

        ArgumentCaptor<TaskDef> captor = ArgumentCaptor.forClass(TaskDef.class);
        verify(taskDefRepository).update(captor.capture());
        TaskDef merged = captor.getValue();
        assertEquals("新任务名", merged.getName());
        assertEquals(20, merged.getRewardPoints());
        assertEquals(TaskDef.STATUS_ENABLED, merged.getStatus());
    }

    @Test
    @DisplayName("update — 任务不存在 → 1103 NO_RESOURCE_FOUND，不触发更新")
    void shouldThrow1103_whenTaskDefMissing() {
        when(taskDefRepository.findByCode(CODE_ORDER)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> taskAdminManager.update(CODE_ORDER, new TaskUpdateReq()));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
        verify(taskDefRepository, never()).update(any());
    }

    @Test
    @DisplayName("update — status=0 停用 → 落库聚合 isEnabled=false（I49 触发口立即 completed=false 口径）")
    void shouldDisableTask_whenStatusZero() {
        TaskDef existing = def(CODE_ORDER, 20);
        assertTrue(existing.isEnabled());
        when(taskDefRepository.findByCode(CODE_ORDER)).thenReturn(existing);
        TaskUpdateReq req = new TaskUpdateReq();
        req.setStatus(TaskDef.STATUS_DISABLED);

        taskAdminManager.update(CODE_ORDER, req);

        ArgumentCaptor<TaskDef> captor = ArgumentCaptor.forClass(TaskDef.class);
        verify(taskDefRepository).update(captor.capture());
        assertFalse(captor.getValue().isEnabled());
    }

    @Test
    @DisplayName("update — rewardPoints 调整立即生效：落库新值（不追溯已发流水，影响后续完成发放）")
    void shouldCarryNewRewardPoints_whenUpdate() {
        when(taskDefRepository.findByCode(CODE_ORDER)).thenReturn(def(CODE_ORDER, 20));
        TaskUpdateReq req = new TaskUpdateReq();
        req.setRewardPoints(50);

        taskAdminManager.update(CODE_ORDER, req);

        ArgumentCaptor<TaskDef> captor = ArgumentCaptor.forClass(TaskDef.class);
        verify(taskDefRepository).update(captor.capture());
        assertEquals(50, captor.getValue().getRewardPoints());
    }

    // ==================== AD51 分页 ====================

    @Test
    @DisplayName("page — 过滤与分页参数透传仓储，记录转换为管理端 VO（不含技术主键）")
    void shouldPassThroughPagingAndConvertToVO_whenPage() {
        Page<TaskDef> repoPage = new Page<>(2, 10);
        repoPage.setRecords(List.of(def(CODE_ORDER, 20)));
        repoPage.setTotal(1);
        when(taskDefRepository.page(0, 2, 10)).thenReturn(repoPage);

        Page<TaskDefVO> result = (Page<TaskDefVO>) taskAdminManager.page(0, 2, 10);

        verify(taskDefRepository).page(0, 2, 10);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        TaskDefVO vo = result.getRecords().get(0);
        assertEquals(CODE_ORDER, vo.getCode());
        assertEquals("每日下单任务", vo.getName());
        assertEquals(TaskDef.TYPE_DAILY, vo.getType());
        assertEquals(20, vo.getRewardPoints());
        assertEquals(CREATE_TIME, vo.getCreateTime());
    }

    @Test
    @DisplayName("page — status=null（不过滤）原样透传仓储")
    void shouldPassNullStatusThrough_whenPageWithoutFilter() {
        Page<TaskDef> repoPage = new Page<>(1, 20);
        repoPage.setRecords(List.of());
        when(taskDefRepository.page(null, 1, 20)).thenReturn(repoPage);

        taskAdminManager.page(null, 1, 20);

        verify(taskDefRepository).page(null, 1, 20);
    }

    // ==================== 测试脚手架 ====================

    /** 创建请求（type/status 走缺省， rewardPoints=20）。 */
    private TaskCreateReq createReq(String code, int rewardPoints) {
        TaskCreateReq req = new TaskCreateReq();
        req.setCode(code);
        req.setName("每日下单任务");
        req.setRewardPoints(rewardPoints);
        return req;
    }

    /** 启用状态的任务定义聚合（createTime 固定，供 VO 断言）。 */
    private TaskDef def(String code, int rewardPoints) {
        return TaskDef.builder()
                .id(1L)
                .code(code)
                .name("每日下单任务")
                .type(TaskDef.TYPE_DAILY)
                .rewardPoints(rewardPoints)
                .status(TaskDef.STATUS_ENABLED)
                .createTime(CREATE_TIME)
                .build();
    }
}
