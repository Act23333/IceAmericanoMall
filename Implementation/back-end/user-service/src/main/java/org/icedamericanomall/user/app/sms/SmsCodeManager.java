package org.icedamericanomall.user.app.sms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.user.adapter.request.SmsCodeSendReq;
import org.icedamericanomall.user.domain.gateway.VerificationGateway;
import org.icedamericanomall.user.domain.model.CaptchaAttempt;
import org.icedamericanomall.user.domain.service.SmsCodeVerifier;
import org.springframework.stereotype.Component;

/**
 * 短信验证码编排（应用层）。
 *
 * <p><b>发码链路（ADR-016 迁移后）</b>：人机验证（verification-service）→
 * 签发（verification-service：生成 + 5 分钟有效 + 60 秒重发限流 + 经 notification-service
 * 投递，写同一 {@code sms:code:} 契约）。本服务不再本地生成验证码、不再本地
 * {@code sms:limit:} 限流、不再直连短信通道（旧 SmsClient 直发链路已删除）。
 * 失败原因按契约结果码映射：RATE_LIMITED → SMS_RATE_LIMITED，SEND_FAILED → SMS_SEND_FAILED。
 *
 * <p><b>校验链路（本地保留）</b>：{@link SmsCodeVerifier} 读同一 {@code sms:code:} 契约，
 * 含 {@code 000000} 灰度后门（用户保留，本轮不动，迁移计划见 ADR-016）；
 * authorization-service 登录侧走 verification-service 在线校验，两端互通。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsCodeManager {

    /**
     * 业务场景（透传 verification-service，仅用于投递文案与埋点归因）。
     *
     * <p>{@code /api/user/code} 被注册 / 短信登录 / 找回密码三处共用，无法在发码时确定单一场景，
     * 故使用自描述的中性场景值（远端对 scene 无白名单校验）。
     */
    private static final String SCENE_USER_CODE = "USER_CODE";

    private final VerificationGateway verificationGateway;
    private final SmsCodeVerifier smsCodeVerifier;

    /**
     * 发送短信验证码（人机验证 → 远端签发投递）。
     *
     * @throws BizException 参数错误 / 人机验证失败（CAPTCHA_ERROR）/ 限流（SMS_RATE_LIMITED）
     *                      / 投递失败（SMS_SEND_FAILED）/ 依赖不可用（SYSTEM_BUSY，fail-closed）
     */
    public void sendCode(SmsCodeSendReq req) {
        String phone = req.getPhone();
        if (phone == null || phone.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "手机号不能为空");
        }
        // 人机验证（极验二次校验已收口 verification-service，ADR-007）
        CaptchaAttempt attempt = new CaptchaAttempt(req.getLotNumber(), req.getCaptchaOutput(),
                req.getPassToken(), req.getGenTime(), null);
        if (!attempt.isComplete()) {
            throw new BizException(ErrorCode.CAPTCHA_ERROR, "人机验证失败");
        }
        verificationGateway.verifyCaptcha(attempt);

        // 签发 + 投递（verification-service，写 sms:code:{phone} 契约）
        verificationGateway.issueSmsCode(phone, SCENE_USER_CODE, req.getRequestId());
    }

    /** 校验短信验证码（本地灰度路径，含 000000 后门；见类注释）。 */
    public void verifyCode(String phone, String code) {
        smsCodeVerifier.verify(phone, code);
    }
}
