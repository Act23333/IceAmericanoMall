package org.icedamericanomall.marketing.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务定义视图对象（适配层出参）—— 管理端 AD51 列表口径（05-API-Specification §4.13.8c）。
 *
 * <p>不透出技术主键 {@code id}；业务锚定为 {@code code}（uk_code）。
 */
@Data
public class TaskDefVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 任务编码（业务唯一键）。 */
    private String code;

    /** 任务名称。 */
    private String name;

    /** 类型：1=每日重置（Phase 1 唯一值）。 */
    private Integer type;

    /** 完成奖励积分。 */
    private Integer rewardPoints;

    /** 状态：1=启用 0=停用。 */
    private Integer status;

    /** 创建时间。 */
    private LocalDateTime createTime;
}
