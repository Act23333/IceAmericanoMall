package org.icedamericanomall.marketing.domain.model.task;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 任务完成记录聚合（纯 POJO）—— {@code task_record} 每日幂等锚点（ADR-047，V5.10）。
 *
 * <p>同一用户同一任务同一业务日最多完成一次（{@code uk_user_task_date}）；并发重复插入由
 * 唯一键拦截，应用层回落为「未完成」（幂等回落纪律）。{@code bizDate} 由服务端按当天计算，
 * 调用方不可传（防伪造日期刷分）。
 */
@Data
@Builder
public class TaskRecord {

    private Long id;

    /** 用户 user_id。 */
    private Long userId;

    /** 任务编码（task_def.code）。 */
    private String taskCode;

    /** 实发奖励积分（取完成时刻的定义值快照）。 */
    private Integer rewardPoints;

    /** 业务日（每日重置锚点，服务端计算）。 */
    private LocalDate bizDate;

    private LocalDateTime createTime;

    /** 记录一次任务完成（id 由仓储回填）。 */
    public static TaskRecord record(Long userId, String taskCode, int rewardPoints, LocalDate bizDate) {
        return TaskRecord.builder()
                .userId(userId)
                .taskCode(taskCode)
                .rewardPoints(rewardPoints)
                .bizDate(bizDate)
                .build();
    }
}
