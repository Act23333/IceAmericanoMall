package org.icedamericanomall.notification.app;

import org.icedamericanomall.notification.app.command.EmailSendCommand;
import org.icedamericanomall.notification.app.command.SmsSendCommand;
import org.icedamericanomall.notification.domain.model.channel.EmailMessage;
import org.icedamericanomall.notification.domain.model.channel.SmsMessage;
import org.icedamericanomall.notification.domain.service.NotificationDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

/**
 * 渠道投递编排单元测试 —— 校验 Command → 领域消息的转换与委托分发。
 *
 * <p>由原 {@code NotificationManagerTest} 的短信/邮件两个用例随用例族拆分迁移而来，断言口径不变。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("通信 — 渠道投递编排")
class ChannelDispatchManagerTest {

    @Mock
    private NotificationDispatcher dispatcher;

    @InjectMocks
    private ChannelDispatchManager channelDispatchManager;

    @Test
    @DisplayName("编排 — SMS 命令：字段透传到领域消息（含 requestId/scene）并委托分发")
    void shouldDelegateToDispatcher_whenSmsCommandGiven() {
        SmsSendCommand command = new SmsSendCommand("13800001111", "验证码 123456", "req-sms-1", "LOGIN");

        channelDispatchManager.sendSms(command);

        verify(dispatcher).sendSms(argThat((SmsMessage message) ->
                "13800001111".equals(message.phone())
                        && "验证码 123456".equals(message.content())
                        && "req-sms-1".equals(message.requestId())
                        && "LOGIN".equals(message.scene())));
    }

    @Test
    @DisplayName("编排 — EMAIL 命令：字段透传到领域消息并委托分发")
    void shouldDelegateToDispatcher_whenEmailCommandGiven() {
        EmailSendCommand command = new EmailSendCommand(
                "a@b.com", "登录验证码", "验证码 123456", "req-email-1", "LOGIN");

        channelDispatchManager.sendEmail(command);

        verify(dispatcher).sendEmail(argThat((EmailMessage message) ->
                "a@b.com".equals(message.to())
                        && "登录验证码".equals(message.subject())
                        && "req-email-1".equals(message.requestId())));
    }
}
