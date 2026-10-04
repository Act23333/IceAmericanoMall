package org.icedamericanomall.user.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.PointsClient;
import org.icedamericanomall.user.domain.port.NewUserRewardService;
import org.springframework.stereotype.Component;

/**
 * 新用户建档奖励适配器 —— 领域端口 {@link NewUserRewardService} 的积分账本落地。
 *
 * <p>发放语义（ADR-017）：新用户建档即发 200 分（积分类型 2=下单奖励沿用历史值），
 * 由 {@code UserProvisioner} 的 <b>新建分支</b>唯一触发（已存在用户不发）。
 *
 * <p>失败语义：奖励是旁路动作，发放失败<b>只告警、不阻断建档</b>
 * （历史语义沿用；积分账本单表插入失败不应阻塞登录/注册主链路）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewUserRewardServiceAdapter implements NewUserRewardService {

    /** 新用户建档奖励分值 */
    private static final int POINTS_WELCOME = 200;

    /** 积分类型：2=下单（历史沿用值，奖励类积分复用） */
    private static final int POINTS_TYPE_REWARD = 2;

    /** 积分来源描述（展示用） */
    private static final String SOURCE_WELCOME = "新用户建档奖励";

    private final PointsClient pointsClient;

    @Override
    public void award(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            pointsClient.addPoints(userId, POINTS_WELCOME, POINTS_TYPE_REWARD, SOURCE_WELCOME);
            log.info("[user][welcome-reward][done] userId={}, points={}", userId, POINTS_WELCOME);
        } catch (Exception e) {
            log.warn("[user][welcome-reward][fail] userId={}, points={}", userId, POINTS_WELCOME, e);
        }
    }
}
