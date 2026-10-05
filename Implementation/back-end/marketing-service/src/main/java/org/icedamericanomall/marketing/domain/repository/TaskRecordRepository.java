package org.icedamericanomall.marketing.domain.repository;

import org.icedamericanomall.marketing.domain.model.task.TaskRecord;

import java.time.LocalDate;

/**
 * 任务完成记录仓储（领域端口）—— 实现方：{@code infrastructure.persistence.task.TaskRecordRepositoryImpl}。
 *
 * <p>幂等锚点 {@code uk_user_task_date(user_id, task_code, biz_date)}：插入冲突时
 * {@code DuplicateKeyException} 上抛给应用层回落（禁止在仓储内吞掉，ADR-047）。
 */
public interface TaskRecordRepository {

    /** 查某用户某任务某业务日的完成记录（无则返回 null）。 */
    TaskRecord findByUserAndCodeAndDate(Long userId, String taskCode, LocalDate bizDate);

    /**
     * 插入完成记录（id 回填）。
     *
     * <p>撞 {@code uk_user_task_date} 唯一键时上抛 {@code DuplicateKeyException}——
     * 由应用层捕获回落为「未完成」（并发幂等回落纪律）。
     *
     * @param record 待插入的完成记录
     */
    void insert(TaskRecord record);

    /** 某用户某任务某业务日是否已有完成记录。 */
    boolean existsByUserAndCodeAndDate(Long userId, String taskCode, LocalDate bizDate);
}
