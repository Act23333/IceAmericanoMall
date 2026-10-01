package org.icedamericanomall.user.app.signin;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.adapter.response.SignResultVO;
import org.icedamericanomall.user.app.points.PointsManager;
import org.icedamericanomall.user.domain.port.SignInStore;
import org.icedamericanomall.user.domain.service.SignInPointsPolicy;
import org.springframework.stereotype.Component;

/**
 * 签到编排（应用层）—— 聚合 Redis Bitmap 签到（{@link SignInStore} 端口）
 * 与积分发放（{@link PointsManager}），递增规则在 {@link SignInPointsPolicy} 领域服务。
 */
@Component
@RequiredArgsConstructor
public class SignManager {

    /** 积分类型：1=签到 */
    private static final int POINTS_TYPE_SIGN = 1;

    private final SignInStore signInStore;
    private final SignInPointsPolicy signInPointsPolicy;
    private final PointsManager pointsManager;

    /** 每日签到：首次签到发放递增积分（10/15/20 封顶），返回签到统计。 */
    public SignResultVO sign(Long userId) {
        boolean isFirstToday = signInStore.markToday(userId);
        long continuousDays = signInStore.countContinuous(userId);
        long earnedPoints = 0;
        if (isFirstToday) {
            earnedPoints = signInPointsPolicy.earnedPoints(continuousDays);
            pointsManager.addPoints(userId, (int) earnedPoints, POINTS_TYPE_SIGN,
                    "每日签到 连续第" + continuousDays + "天");
        }
        return build(isFirstToday, earnedPoints, continuousDays, userId);
    }

    /** 查询签到状态（不发放积分）。 */
    public SignResultVO status(Long userId) {
        return build(signInStore.isSignedToday(userId), 0,
                signInStore.countContinuous(userId), userId);
    }

    private SignResultVO build(boolean signed, long earnedPoints, long continuousDays, Long userId) {
        SignResultVO vo = new SignResultVO();
        vo.setSigned(signed);
        vo.setEarnedPoints(earnedPoints);
        vo.setContinuousDays(continuousDays);
        vo.setMonthCount(signInStore.countCurrentMonth(userId));
        return vo;
    }
}
