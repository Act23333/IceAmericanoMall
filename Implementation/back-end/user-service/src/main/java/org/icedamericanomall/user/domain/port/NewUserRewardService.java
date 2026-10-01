package org.icedamericanomall.user.domain.port;

/**
 * 新用户建档奖励能力（领域层定义，基础设施层实现）。
 *
 * <p>语义（V5.2 / ADR-017）：奖励统一挂到"<b>新用户建档</b>"这一事件 ——
 * 任何登录标识（手机号 / 邮箱 / 微信）在本平台首次建档时发放一次，已存在的用户不发。
 * 原"回归登录奖励（距上次登录 &gt;30 天 100 分）"随登录链路退出 user-service 而退役
 * （登录已由 authorization-service 本地凭证闭环承载，无登录回调点）。
 *
 * <p>失败语义：奖励发放是<b>尽力而为</b>的旁路动作，失败只告警、不得阻断建档
 * （实现方负责捕获并记录，见基础设施实现）。
 */
public interface NewUserRewardService {

    /**
     * 为新建档用户发放欢迎奖励（幂等由调用方"仅新建分支触发"保证）。
     *
     * @param userId 新用户技术主键
     */
    void award(Long userId);
}
