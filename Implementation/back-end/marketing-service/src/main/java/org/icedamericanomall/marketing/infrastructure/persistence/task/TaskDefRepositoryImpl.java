package org.icedamericanomall.marketing.infrastructure.persistence.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.marketing.domain.model.task.TaskDef;
import org.icedamericanomall.marketing.domain.repository.TaskDefRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 任务定义仓储实现 —— PO 与聚合互转在本类完成（内联 static toPO/toDomain）。
 */
@Repository
@RequiredArgsConstructor
public class TaskDefRepositoryImpl implements TaskDefRepository {

    private final TaskDefMapper taskDefMapper;

    @Override
    public TaskDef findByCode(String code) {
        return toDomain(taskDefMapper.selectOne(new LambdaQueryWrapper<TaskDefPO>()
                .eq(TaskDefPO::getCode, code)));
    }

    @Override
    public List<TaskDef> findAllEnabled() {
        return taskDefMapper.selectList(new LambdaQueryWrapper<TaskDefPO>()
                        .eq(TaskDefPO::getStatus, TaskDef.STATUS_ENABLED)
                        .orderByAsc(TaskDefPO::getCode))
                .stream().map(TaskDefRepositoryImpl::toDomain).toList();
    }

    // ==================== PO <-> 聚合 ====================

    static TaskDef toDomain(TaskDefPO po) {
        if (po == null) {
            return null;
        }
        return TaskDef.builder()
                .id(po.getId())
                .code(po.getCode())
                .name(po.getName())
                .type(po.getType())
                .rewardPoints(po.getRewardPoints())
                .status(po.getStatus())
                .createTime(po.getCreateTime())
                .build();
    }

    static TaskDefPO toPO(TaskDef taskDef) {
        TaskDefPO po = new TaskDefPO();
        po.setId(taskDef.getId());
        po.setCode(taskDef.getCode());
        po.setName(taskDef.getName());
        po.setType(taskDef.getType());
        po.setRewardPoints(taskDef.getRewardPoints());
        po.setStatus(taskDef.getStatus());
        po.setCreateTime(taskDef.getCreateTime());
        return po;
    }
}
