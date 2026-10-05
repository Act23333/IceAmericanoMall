package org.icedamericanomall.marketing.domain.repository;

import org.icedamericanomall.marketing.domain.model.task.TaskDef;

import java.util.List;

/**
 * 任务定义仓储（领域端口）—— 实现方：{@code infrastructure.persistence.task.TaskDefRepositoryImpl}。
 *
 * <p>按业务唯一键 {@code code} 寻址（禁止按技术主键操作，ADR-047）。
 */
public interface TaskDefRepository {

    /** 按任务编码取定义（不存在返回 null）。 */
    TaskDef findByCode(String code);

    /** 全部启用状态的任务定义（M14 任务中心列表，按 code 升序保证稳定展示）。 */
    List<TaskDef> findAllEnabled();
}
