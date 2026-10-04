package org.icedamericanomall.marketing.domain.service;

/**
 * 签到积分策略（领域服务）—— 连续签到奖励递增规则。
 *
 * <p>规则：第 1 天 10 分，之后每天 +5 分，单日封顶 20 分（Day3+ 恒为 20）。
 * 纯计算逻辑（无状态、无依赖），<b>刻意不带任何框架注解</b>：Bean 装配见
 * {@code infrastructure/config/DomainBeanConfig}，单元测试可直接 {@code new}。
 *
 * <p>归属说明（ADR-016 收尾）：签到是营销激励活动，奖励规则与积分账本同域——
 * 规则调整（活动加码/封顶变化）不再跨服务，发放为本服务本地调用。
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
