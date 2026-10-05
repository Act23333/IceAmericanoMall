package org.icedamericanomall.marketing.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
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

    /**
     * 管理端分页查询（AD51，V5.13 任务运营闭环）。
     *
     * @param status   状态过滤（null=不过滤，全部状态）
     * @param pageNum  页码（1 起）
     * @param pageSize 页大小
     * @return 分页结果（code 升序稳定排序，与 M14 展示口径一致）
     */
    IPage<TaskDef> page(Integer status, int pageNum, int pageSize);

    /**
     * 新增任务定义（INSERT；{@code code} 唯一性由 {@code uk_code} 唯一键兜底）。
     *
     * @param taskDef 待保存的任务定义聚合（生成主键回填聚合）
     */
    void save(TaskDef taskDef);

    /**
     * 更新任务定义（编辑用例 null-safe 合并后的聚合全量携带；{@code code} 业务唯一键寻址）。
     *
     * @param taskDef 合并后的任务定义聚合
     */
    void update(TaskDef taskDef);
}
