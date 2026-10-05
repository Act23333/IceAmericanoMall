package org.icedamericanomall.marketing.infrastructure.persistence.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.marketing.domain.model.task.TaskRecord;
import org.icedamericanomall.marketing.domain.repository.TaskRecordRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

/**
 * 任务完成记录仓储实现 —— PO 与聚合互转在本类完成（内联 static toPO/toDomain）。
 *
 * <p>命中索引 {@code uk_user_task_date} / {@code idx_user_date}。
 */
@Repository
@RequiredArgsConstructor
public class TaskRecordRepositoryImpl implements TaskRecordRepository {

    private final TaskRecordMapper taskRecordMapper;

    @Override
    public TaskRecord findByUserAndCodeAndDate(Long userId, String taskCode, LocalDate bizDate) {
        return toDomain(taskRecordMapper.selectOne(new LambdaQueryWrapper<TaskRecordPO>()
                .eq(TaskRecordPO::getUserId, userId)
                .eq(TaskRecordPO::getTaskCode, taskCode)
                .eq(TaskRecordPO::getBizDate, bizDate)));
    }

    @Override
    public void insert(TaskRecord record) {
        TaskRecordPO po = toPO(record);
        // uk_user_task_date 冲突 → DuplicateKeyException 上抛（幂等锚点，应用层回落）
        taskRecordMapper.insert(po);
        record.setId(po.getId());
    }

    @Override
    public boolean existsByUserAndCodeAndDate(Long userId, String taskCode, LocalDate bizDate) {
        return taskRecordMapper.selectCount(new LambdaQueryWrapper<TaskRecordPO>()
                .eq(TaskRecordPO::getUserId, userId)
                .eq(TaskRecordPO::getTaskCode, taskCode)
                .eq(TaskRecordPO::getBizDate, bizDate)) > 0;
    }

    // ==================== PO <-> 聚合 ====================

    static TaskRecord toDomain(TaskRecordPO po) {
        if (po == null) {
            return null;
        }
        return TaskRecord.builder()
                .id(po.getId())
                .userId(po.getUserId())
                .taskCode(po.getTaskCode())
                .rewardPoints(po.getRewardPoints())
                .bizDate(po.getBizDate())
                .createTime(po.getCreateTime())
                .build();
    }

    static TaskRecordPO toPO(TaskRecord record) {
        TaskRecordPO po = new TaskRecordPO();
        po.setId(record.getId());
        po.setUserId(record.getUserId());
        po.setTaskCode(record.getTaskCode());
        po.setRewardPoints(record.getRewardPoints());
        po.setBizDate(record.getBizDate());
        po.setCreateTime(record.getCreateTime());
        return po;
    }
}
