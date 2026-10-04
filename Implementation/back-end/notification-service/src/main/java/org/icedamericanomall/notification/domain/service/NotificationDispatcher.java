package org.icedamericanomall.notification.domain.service;

import org.icedamericanomall.notification.domain.model.channel.EmailMessage;
import org.icedamericanomall.notification.domain.model.channel.SmsMessage;
import org.icedamericanomall.notification.domain.port.EmailChannelPort;
import org.icedamericanomall.notification.domain.port.SmsChannelPort;

/**
 * 通道分发领域服务（纯 POJO，无框架注解，由基础设施层 {@code DomainBeanConfig} 装配）。
 *
 * <p>职责：把领域消息路由到对应通道端口。通道与端点一一对应
 * （{@code /internal/notify/sms} → {@link SmsChannelPort}、{@code /internal/notify/email} →
 * {@link EmailChannelPort}），适配层按端点直接调用对应方法，无需再做字符串通道解析；
 * 站内信不属通道投递（走应用层落库，见 {@code InAppMessageRepository}）。
 */
public class NotificationDispatcher {

    private final SmsChannelPort smsChannelPort;
    private final EmailChannelPort emailChannelPort;

    public NotificationDispatcher(SmsChannelPort smsChannelPort, EmailChannelPort emailChannelPort) {
        this.smsChannelPort = smsChannelPort;
        this.emailChannelPort = emailChannelPort;
    }

    /** 投递短信。 */
    public void sendSms(SmsMessage message) {
        smsChannelPort.send(message);
    }

    /** 投递邮件。 */
    public void sendEmail(EmailMessage message) {
        emailChannelPort.send(message);
    }
}
