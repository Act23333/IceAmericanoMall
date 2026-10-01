package org.icedamericanomall.verification.domain.gateway;

import org.icedamericanomall.verification.domain.model.VerificationCode;

/**
 * 通知投递网关（跨上下文调用的防腐层）—— verification-service → notification-service。
 *
 * <p>入参使用本上下文的领域对象，外部模型（{@code NotificationSendReqCO}）与
 * 通道差异（短信/邮件文案）由实现类在基础设施层完成转换，
 * 领域层不感知 Feign、HTTP 与模板。
 */
public interface NotificationGateway {

    /**
     * 投递验证码到目标通道。
     *
     * @param code      验证码（含通道与目标）
     * @param requestId 幂等 / 链路追踪标识
     * @param scene     业务场景（LOGIN / REGISTER / RESET_PASSWORD ...）
     * @throws org.icedamericanomall.common.exception.BizException 投递失败
     */
    void sendVerificationCode(VerificationCode code, String requestId, String scene);
}
