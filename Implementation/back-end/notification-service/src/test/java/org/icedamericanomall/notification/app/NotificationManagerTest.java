package org.icedamericanomall.notification.app;

import org.icedamericanomall.notification.app.command.EmailSendCommand;
import org.icedamericanomall.notification.app.command.SmsSendCommand;
import org.icedamericanomall.notification.domain.model.EmailMessage;
import org.icedamericanomall.notification.domain.model.InAppMessage;
import org.icedamericanomall.notification.domain.model.SmsMessage;
import org.icedamericanomall.notification.domain.port.EmailChannelPort;
import org.icedamericanomall.notification.domain.port.SmsChannelPort;
import org.icedamericanomall.notification.domain.service.NotificationDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.icedamericanomall.common.exception.BizException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 应用层编排单元测试 —— 校验 Command → 领域消息的转换与委托。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("通信 — 应用层编排")
class NotificationManagerTest {

    @Mock
    private NotificationDispatcher dispatcher;

    @Mock
    private SmsChannelPort smsChannelPort;

    @Mock
    private EmailChannelPort emailChannelPort;

    @InjectMocks
    private NotificationManager notificationManager;

    @Test
    @DisplayName("编排 — SMS 命令：字段透传到领域消息（含 requestId/scene）并委托分发")
    void shouldDelegateToDispatcher_whenSmsCommandGiven() {
        SmsSendCommand command = new SmsSendCommand("13800001111", "验证码 123456", "req-sms-1", "LOGIN");

        notificationManager.sendSms(command);

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

        notificationManager.sendEmail(command);

        verify(dispatcher).sendEmail(argThat((EmailMessage message) ->
                "a@b.com".equals(message.to())
                        && "登录验证码".equals(message.subject())
                        && "req-email-1".equals(message.requestId())));
    }

    @Test
    @DisplayName("编排 — IN_APP：领域层拒绝（未实现），不产生任何投递副作用")
    void shouldRejectInAppMessage_whenChannelNotImplemented() {
        // 用真实领域服务（Mock 的 dispatcher 不会执行「未实现」规则）
        NotificationManager manager = new NotificationManager(
                new NotificationDispatcher(smsChannelPort, emailChannelPort));
        InAppMessage message = new InAppMessage(1001L, 2002L, "商家", "REPLY", "回复", "内容", "/product/1");

        assertThrows(BizException.class, () -> manager.sendInAppMessage(message));

        verifyNoInteractions(smsChannelPort);
        verifyNoInteractions(emailChannelPort);
    }
}
