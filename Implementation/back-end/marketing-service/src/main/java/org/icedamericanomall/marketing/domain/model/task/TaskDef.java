package org.icedamericanomall.marketing.domain.model.task;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务定义聚合（纯 POJO）—— {@code task_def} 表驱动任务（ADR-047，V5.10）。
 *
 * <p>运营加任务 = 加一行 seed，代码零改动；{@code code} 为业务唯一键（调用方锚定，
 * 如 DAILY_ORDER / DAILY_REVIEW）。启用状态由服务端权威校验，调用方只传 userId + taskCode。
 */
@Data
@Builder
public class TaskDef {

    /** 任务状态：1=启用。 */
    public static final int STATUS_ENABLED = 1;

    private Long id;

    /** 任务编码（uk_code，业务唯一键）。 */
    private String code;

    /** 任务名称。 */
    private String name;

    /** 类型：1=每日重置（Phase 1 唯一值）。 */
    private Integer type;

    /** 完成奖励积分。 */
    private Integer rewardPoints;

    /** 状态：1=启用 0=停用。 */
    private Integer status;

    private LocalDateTime createTime;

    /** 是否启用（完成发放的前置条件）。 */
    public boolean isEnabled() {
        return status != null && status == STATUS_ENABLED;
    }
}
