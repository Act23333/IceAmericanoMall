package org.icedamericanomall.verification.infrastructure.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.NotificationClient;
import org.icedamericanomall.dto.NotificationSendReqCO;
import org.icedamericanomall.verification.domain.enums.VerificationChannel;
import org.icedamericanomall.verification.domain.gateway.NotificationGateway;
import org.icedamericanomall.verification.domain.model.VerificationCode;
import org.springframework.stereotype.Component;

/**
 * 通知投递网关实现（基础设施层，防腐层）—— 领域对象 → ia-api Feign 契约。
 *
 * <p>调用链：verification-service →（Feign）notification-service → ia-integration → 第三方。
 * 通道差异（短信纯文本 / 邮件主题 + 正文）与文案模板都在本类完成，
 * 领域层只表达「把验证码投递到目标」。
 *
 * <p>文案约定：短信模板变量固定为 {@code code}（见 ia-integration 的 {@code AliyunSmsClient}），
 * 因此短信正文直接投递验证码本身；邮件则带主题与说明文案。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationGatewayImpl implements NotificationGateway {

    /** 第三方短信模板只声明了 code 一个变量，故短信正文就是验证码 */
    private static final String SMS_SUBJECT = null;

    private static final String EMAIL_SUBJECT = "冰美商城验证码";

    private final NotificationClient notificationClient;

    @Override
    public void sendVerificationCode(VerificationCode code, String requestId, String scene) {
        NotificationSendReqCO request = new NotificationSendReqCO();
        request.setTarget(code.target());
        request.setRequestId(requestId);
        request.setScene(scene);

        if (code.channel() == VerificationChannel.SMS) {
            request.setSubject(SMS_SUBJECT);
            request.setContent(code.code());
            notificationClient.sendSms(request);
            return;
        }

        request.setSubject(EMAIL_SUBJECT);
        request.setContent("您的验证码是 " + code.code() + "，5 分钟内有效。如非本人操作请忽略本邮件。");
        notificationClient.sendEmail(request);
    }
}
