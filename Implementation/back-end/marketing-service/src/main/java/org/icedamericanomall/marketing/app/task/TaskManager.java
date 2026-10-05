package org.icedamericanomall.marketing.app.task;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.dto.TaskCompleteCO;
import org.icedamericanomall.dto.TaskCompleteResultCO;
import org.icedamericanomall.marketing.adapter.response.DailyTaskVO;
import org.icedamericanomall.marketing.app.points.PointsManager;
import org.icedamericanomall.marketing.domain.model.task.TaskDef;
import org.icedamericanomall.marketing.domain.model.task.TaskRecord;
import org.icedamericanomall.marketing.domain.repository.TaskDefRepository;
import org.icedamericanomall.marketing.domain.repository.TaskRecordRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日任务用例族编排（应用层，F9 任务体系，ADR-047，V5.10）。
 *
 * <p>I49 完成任务：任务记录与积分发放走<b>同一本地事务</b>（纯本地写；
 * {@link PointsManager#addPoints} 以 REQUIRED 传播加入本事务）；
 * 顺序重放与并发唯一键冲突均幂等回落为「未完成」（ADR-035 回落纪律）——
 * 任务不存在/停用同样返回 false，不向调用方抛业务异常（幂等语义，调用方旁路不关心原因）。
 *
 * <p>M14 任务中心：全部启用任务 + 当日完成状态（量级小，不加缓存）。
 */
@Component
@RequiredArgsConstructor
public class TaskManager {

    /** 积分类型：3=任务奖励（1=签到 2=下单，对齐 PointsLog 历史口径）。 */
    private static final int POINTS_TYPE_TASK = 3;

    /** 积分流水来源前缀：task:{taskCode}。 */
    private static final String POINTS_SOURCE_PREFIX = "task:";

    private final TaskDefRepository taskDefRepository;
    private final TaskRecordRepository taskRecordRepository;
    private final PointsManager pointsManager;

    /**
     * I49：完成任务并发放积分（幂等）。
     *
     * <p>{@code bizDate} 由服务端按当天计算（调用方不可传，防伪造日期刷分）。
     * 幂等三出口统一回落：顺序重放（当日已有记录）/ 并发冲突
     * （{@code uk_user_task_date} 唯一键）/ 任务不存在或停用 → {@code completed=false}。
     *
     * @param co 完成参数（userId + taskCode）
     * @return 完成结果：首次 = true + 实发积分；重复/停用/不存在 = false + 0
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskCompleteResultCO completeTask(TaskCompleteCO co) {
        LocalDate bizDate = LocalDate.now();
        TaskDef taskDef = taskDefRepository.findByCode(co.getTaskCode());
        if (taskDef == null || !taskDef.isEnabled()) {
            return notCompleted();
        }
        if (taskRecordRepository.findByUserAndCodeAndDate(co.getUserId(), co.getTaskCode(), bizDate) != null) {
            return notCompleted();
        }
        try {
            taskRecordRepository.insert(TaskRecord.record(co.getUserId(), co.getTaskCode(),
                    taskDef.getRewardPoints(), bizDate));
        } catch (DuplicateKeyException e) {
            // 并发孪生请求已插记录（uk_user_task_date 兜底）：幂等回落，积分不得发放
            return notCompleted();
        }
        pointsManager.addPoints(co.getUserId(), taskDef.getRewardPoints(), POINTS_TYPE_TASK,
                POINTS_SOURCE_PREFIX + co.getTaskCode());
        return new TaskCompleteResultCO(true, taskDef.getRewardPoints());
    }

    /**
     * M14：当前用户今日任务列表（全部启用任务 + 当日完成状态）。
     *
     * @param userId 当前登录用户（UserContext）
     * @return 任务列表（code 升序，completed=当日是否已完成）
     */
    public List<DailyTaskVO> dailyTasks(Long userId) {
        LocalDate bizDate = LocalDate.now();
        return taskDefRepository.findAllEnabled().stream()
                .map(def -> toDailyTaskVO(def, userId, bizDate))
                .toList();
    }

    // ==================== 内部方法 ====================

    /** 未完成统一出参：completed=false + 0 分（幂等语义，不外抛业务异常）。 */
    private TaskCompleteResultCO notCompleted() {
        return new TaskCompleteResultCO(false, 0);
    }

    /** 任务定义 + 当日完成状态 → 任务中心视图。 */
    private DailyTaskVO toDailyTaskVO(TaskDef def, Long userId, LocalDate bizDate) {
        DailyTaskVO vo = new DailyTaskVO();
        vo.setCode(def.getCode());
        vo.setName(def.getName());
        vo.setRewardPoints(def.getRewardPoints());
        vo.setCompleted(taskRecordRepository.existsByUserAndCodeAndDate(userId, def.getCode(), bizDate));
        return vo;
    }
}
