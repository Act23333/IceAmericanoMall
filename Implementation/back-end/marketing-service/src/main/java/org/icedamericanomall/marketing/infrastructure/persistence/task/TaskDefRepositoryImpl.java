package org.icedamericanomall.marketing.infrastructure.persistence.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    /** 管理端分页（AD51）：status 可选过滤，code 升序稳定排序（uk_code 唯一，次序确定）。 */
    @Override
    public IPage<TaskDef> page(Integer status, int pageNum, int pageSize) {
        LambdaQueryWrapper<TaskDefPO> wrapper = new LambdaQueryWrapper<TaskDefPO>()
                .orderByAsc(TaskDefPO::getCode);
        if (status != null) {
            wrapper.eq(TaskDefPO::getStatus, status);
        }
        return taskDefMapper.selectPage(new Page<>(pageNum, pageSize), wrapper)
                .convert(TaskDefRepositoryImpl::toDomain);
    }

    @Override
    public void save(TaskDef taskDef) {
        TaskDefPO po = toPO(taskDef);
        taskDefMapper.insert(po);
        taskDef.setId(po.getId());
    }

    @Override
    public void update(TaskDef taskDef) {
        taskDefMapper.updateById(toPO(taskDef));
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
