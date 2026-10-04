package org.icedamericanomall.notification.domain;

import org.icedamericanomall.notification.domain.model.channel.EmailMessage;
import org.icedamericanomall.notification.domain.model.channel.SmsMessage;
import org.icedamericanomall.notification.domain.port.EmailChannelPort;
import org.icedamericanomall.notification.domain.port.SmsChannelPort;
import org.icedamericanomall.notification.domain.service.NotificationDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 通道分发领域服务单元测试 —— 纯 POJO，不依赖 Spring 容器。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("通信 — 通道分发领域服务")
class NotificationDispatcherTest {

    @Mock
    private SmsChannelPort smsChannelPort;

    @Mock
    private EmailChannelPort emailChannelPort;

    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher(smsChannelPort, emailChannelPort);
    }

    @Test
    @DisplayName("投递短信 — sendSms → 仅路由到短信端口")
    void shouldRouteToSmsPort_whenSendSms() {
        SmsMessage message = new SmsMessage("13800001111", "验证码 123456", "req-1", "LOGIN");

        dispatcher.sendSms(message);

        verify(smsChannelPort).send(message);
        verify(emailChannelPort, never()).send(any());
    }

    @Test
    @DisplayName("投递邮件 — sendEmail → 仅路由到邮件端口")
    void shouldRouteToEmailPort_whenSendEmail() {
        EmailMessage message = new EmailMessage("a@b.com", "登录验证码", "验证码 123456", "req-2", "LOGIN");

        dispatcher.sendEmail(message);

        verify(emailChannelPort).send(message);
        verify(smsChannelPort, never()).send(any());
    }
}
