package org.icedAmericanoMall.manager;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.vo.SignResultVO;
import org.icedAmericanoMall.service.PointsService;
import org.icedAmericanoMall.service.UserSignService;
import org.springframework.stereotype.Component;

/**
 * 签到编排 Manager —— 聚合 Redis 签到（{@link UserSignService}）与积分发放（{@link PointsService}）。
 *
 * <p>连续签到奖励递增：第 1 天 10 分，之后每天 +5 分，单日封顶 20 分（Day3+ 恒为 20）。
 */
@Component
@RequiredArgsConstructor
public class SignManager {

    private static final int BASE_POINTS = 10;
    private static final int STEP_POINTS = 5;
    private static final int MAX_DAILY_POINTS = 20;
    private static final int POINTS_TYPE_SIGN = 1;

    private final UserSignService userSignService;
    private final PointsService pointsService;

    /** 每日签到：首次签到发放递增积分，返回签到统计。 */
    public SignResultVO sign(Long userId) {
        boolean isFirstToday = userSignService.sign(userId);
        long continuousDays = userSignService.countContinuousSign(userId);
        long earnedPoints = 0;
        if (isFirstToday) {
            earnedPoints = escalatingPoints(continuousDays);
            pointsService.addPoints(userId, (int) earnedPoints, POINTS_TYPE_SIGN,
                    "每日签到 连续第" + continuousDays + "天");
        }
        return build(isFirstToday, earnedPoints, continuousDays, userId);
    }

    /** 查询签到状态（不发放积分）。 */
    public SignResultVO status(Long userId) {
        return build(userSignService.isTodaySigned(userId), 0,
                userSignService.countContinuousSign(userId), userId);
    }

    /** 连续第 n 天的签到积分：10 / 15 / 20（Day3+ 封顶 20）。 */
    private long escalatingPoints(long continuousDays) {
        return Math.min(BASE_POINTS + (continuousDays - 1) * STEP_POINTS, MAX_DAILY_POINTS);
    }

    private SignResultVO build(boolean signed, long earnedPoints, long continuousDays, Long userId) {
        SignResultVO vo = new SignResultVO();
        vo.setSigned(signed);
        vo.setEarnedPoints(earnedPoints);
        vo.setContinuousDays(continuousDays);
        vo.setMonthCount(userSignService.countCurrentMonthSign(userId));
        return vo;
    }
}
