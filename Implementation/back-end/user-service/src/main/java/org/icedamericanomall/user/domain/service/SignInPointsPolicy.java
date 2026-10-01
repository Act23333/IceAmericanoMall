package org.icedamericanomall.user.domain.service;

/**
 * 签到积分策略（领域服务）—— 连续签到奖励递增规则。
 *
 * <p>规则：第 1 天 10 分，之后每天 +5 分，单日封顶 20 分（Day3+ 恒为 20）。
 * 纯计算逻辑（无状态、无依赖），由基础设施层 {@code DomainBeanConfig} 装配。
 */
public class SignInPointsPolicy {

    /** 首日基础积分 */
    private static final int BASE_POINTS = 10;

    /** 每日递增量 */
    private static final int STEP_POINTS = 5;

    /** 单日封顶 */
    private static final int MAX_DAILY_POINTS = 20;

    /**
     * 连续第 n 天的签到积分。
     *
     * @param continuousDays 连续签到天数（>=1）
     * @return 应发放积分
     */
    public long earnedPoints(long continuousDays) {
        return Math.min(BASE_POINTS + (continuousDays - 1) * STEP_POINTS, MAX_DAILY_POINTS);
    }
}
