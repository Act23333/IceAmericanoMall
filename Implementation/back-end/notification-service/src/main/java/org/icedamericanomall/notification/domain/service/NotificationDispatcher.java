package org.icedamericanomall.notification.domain.service;

import org.icedamericanomall.notification.domain.enums.NotificationChannel;
import org.icedamericanomall.notification.domain.model.EmailMessage;
import org.icedamericanomall.notification.domain.model.InAppMessage;
import org.icedamericanomall.notification.domain.model.SmsMessage;
import org.icedamericanomall.notification.domain.port.EmailChannelPort;
import org.icedamericanomall.notification.domain.port.SmsChannelPort;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

/**
 * 通道分发领域服务（纯 POJO，无框架注解，由基础设施层 {@code DomainBeanConfig} 装配）。
 *
 * <p>职责：把领域消息路由到对应通道端口，并承载「通道是否可用」的业务规则。
 * 之所以把「站内信未实现」放在领域层而不是应用层：这是业务能力边界，不是编排细节。
 *
 * <p>通道选择规则：
 * <ul>
 *   <li>{@link NotificationChannel#SMS}  → {@link SmsChannelPort}</li>
 *   <li>{@link NotificationChannel#EMAIL} → {@link EmailChannelPort}</li>
 *   <li>{@link NotificationChannel#IN_APP} → 抛业务异常（V5.0 规划，见 {@code 05-API-Specification} §4.2.9）</li>
 * </ul>
 */
public class NotificationDispatcher {

    private final SmsChannelPort smsChannelPort;
    private final EmailChannelPort emailChannelPort;

    public NotificationDispatcher(SmsChannelPort smsChannelPort, EmailChannelPort emailChannelPort) {
        this.smsChannelPort = smsChannelPort;
        this.emailChannelPort = emailChannelPort;
    }

    /**
     * 按通道投递消息。
     *
     * @param channel 目标通道
     * @param payload 领域消息（{@link SmsMessage} / {@link EmailMessage} / {@link InAppMessage}）
     * @throws BizException 通道未实现或消息类型与通道不匹配（ErrorCode.PARAM_ERROR / BUSINESS_EXECUTION_EXCEPTION）
     */
    public void dispatch(NotificationChannel channel, Object payload) {
        if (channel == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "通知通道不能为空");
        }
        switch (channel) {
            case SMS -> sendSms(asMessage(payload, SmsMessage.class, channel));
            case EMAIL -> sendEmail(asMessage(payload, EmailMessage.class, channel));
            case IN_APP -> throw notImplemented();
        }
    }

    /** 投递短信。 */
    public void sendSms(SmsMessage message) {
        smsChannelPort.send(message);
    }

    /** 投递邮件。 */
    public void sendEmail(EmailMessage message) {
        emailChannelPort.send(message);
    }

    /**
     * 投递站内信 —— 当前一律拒绝。
     *
     * @throws BizException 通道未实现（ErrorCode.BUSINESS_EXECUTION_EXCEPTION）
     */
    public void sendInApp(InAppMessage message) {
        throw notImplemented();
    }

    private BizException notImplemented() {
        return new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                "站内信通道尚未实现（V5.0 规划），请改用 SMS / EMAIL 通道");
    }

    private <T> T asMessage(Object payload, Class<T> expected, NotificationChannel channel) {
        if (!expected.isInstance(payload)) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "消息类型与通道不匹配: channel=" + channel.getCode());
        }
        return expected.cast(payload);
    }
}
