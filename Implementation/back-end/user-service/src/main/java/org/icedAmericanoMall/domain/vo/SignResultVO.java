package org.icedAmericanoMall.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 每日签到结果视图对象。
 */
@Data
public class SignResultVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 本次是否为当日首次签到（重复签到为 false）。 */
    private boolean signed;
    /** 本次签到获得的积分（重复签到为 0）。 */
    private long earnedPoints;
    /** 当月累计签到次数。 */
    private long monthCount;
    /** 当前连续签到天数。 */
    private long continuousDays;
}
