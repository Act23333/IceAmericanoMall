package org.icedamericanomall.user.domain.port;

/**
 * 签到存储能力（领域层定义，基础设施层用 Redis Bitmap 实现）。
 *
 * <p>领域只表达签到语义（"今天有没有签过 / 连签了几天 / 本月签了几次"），
 * 不感知 Bitmap 位运算与 Key 布局（{@code user:sign:{userId}:{yyyy:MM}}）。
 */
public interface SignInStore {

    /**
     * 标记今日已签到。
     *
     * @param userId 用户 ID
     * @return true=本次是新签到；false=今日已签过（重复签到幂等）
     */
    boolean markToday(Long userId);

    /** 今日是否已签到。 */
    boolean isSignedToday(Long userId);

    /** 当月累计签到次数。 */
    long countCurrentMonth(Long userId);

    /** 当前连续签到天数（自今日/最近一次签到向前数）。 */
    long countContinuous(Long userId);
}
