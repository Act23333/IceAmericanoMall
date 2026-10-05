package org.icedamericanomall.marketing.adapter.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 每日任务视图对象（适配层出参）—— {@code GET /api/task/daily} 响应元素
 * （契约 §4.10.3 M14，V5.10，ADR-047）。
 */
@Data
public class DailyTaskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务编码（DAILY_ORDER / DAILY_REVIEW）。 */
    private String code;

    /** 任务名称。 */
    private String name;

    /** 完成奖励积分。 */
    private Integer rewardPoints;

    /** 今日是否已完成。 */
    private boolean completed;
}
