package org.icedamericanomall.marketing.app.sign;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.marketing.app.dto.SignResultDTO;
import org.icedamericanomall.marketing.app.points.PointsManager;
import org.icedamericanomall.marketing.domain.port.SignInStore;
import org.icedamericanomall.marketing.domain.service.SignInPointsPolicy;
import org.springframework.stereotype.Component;

/**
 * 签到编排（应用层）—— 签到进度（{@link SignInStore} 端口，Redis Bitmap）
 * + 奖励规则（{@link SignInPointsPolicy} 领域服务）+ 积分发放（{@link PointsManager} 本地调用）。
 *
 * <p>归属说明（ADR-016 收尾）：签到是营销激励活动，与积分账本同域——
 * 发放为本服务本地调用，不再跨服务。
 */
@Component
@RequiredArgsConstructor
public class SignManager {

    /** 积分类型：1=签到。 */
    private static final int POINTS_TYPE_SIGN = 1;

    private final SignInStore signInStore;
    private final SignInPointsPolicy signInPointsPolicy;
    private final PointsManager pointsManager;

    /** 每日签到：首次签到发放递增积分（10/15/20 封顶），返回签到统计。 */
    public SignResultDTO sign(Long userId) {
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
    public SignResultDTO status(Long userId) {
        return build(signInStore.isSignedToday(userId), 0,
                signInStore.countContinuous(userId), userId);
    }

    private SignResultDTO build(boolean signed, long earnedPoints, long continuousDays, Long userId) {
        SignResultDTO dto = new SignResultDTO();
        dto.setSigned(signed);
        dto.setEarnedPoints(earnedPoints);
        dto.setContinuousDays(continuousDays);
        dto.setMonthCount(signInStore.countCurrentMonth(userId));
        return dto;
    }
}
