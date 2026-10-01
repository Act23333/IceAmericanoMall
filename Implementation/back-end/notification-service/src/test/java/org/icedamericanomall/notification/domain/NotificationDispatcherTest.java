package org.icedamericanomall.notification.domain;

import org.icedamericanomall.notification.domain.enums.NotificationChannel;
import org.icedamericanomall.notification.domain.model.EmailMessage;
import org.icedamericanomall.notification.domain.model.InAppMessage;
import org.icedamericanomall.notification.domain.model.SmsMessage;
import org.icedamericanomall.notification.domain.port.EmailChannelPort;
import org.icedamericanomall.notification.domain.port.SmsChannelPort;
import org.icedamericanomall.notification.domain.service.NotificationDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.icedamericanomall.common.exception.BizException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    @DisplayName("通道选择 — channel=SMS → 路由到短信端口")
    void shouldRouteToSmsPort_whenChannelIsSms() {
        SmsMessage message = new SmsMessage("13800001111", "验证码 123456", "req-1", "LOGIN");

        dispatcher.dispatch(NotificationChannel.SMS, message);

        verify(smsChannelPort).send(message);
        verify(emailChannelPort, never()).send(any());
    }

    @Test
    @DisplayName("通道选择 — channel=EMAIL → 路由到邮件端口")
    void shouldRouteToEmailPort_whenChannelIsEmail() {
        EmailMessage message = new EmailMessage("a@b.com", "登录验证码", "验证码 123456", "req-2", "LOGIN");

        dispatcher.dispatch(NotificationChannel.EMAIL, message);

        verify(emailChannelPort).send(message);
        verify(smsChannelPort, never()).send(any());
    }

    @Test
    @DisplayName("通道选择 — channel=IN_APP → 抛业务异常（通道未实现）")
    void shouldThrowBizException_whenChannelIsInApp() {
        InAppMessage message = new InAppMessage(1001L, 2002L, "商家", "REPLY", "回复", "内容", "/product/1");

        BizException ex = assertThrows(BizException.class,
                () -> dispatcher.dispatch(NotificationChannel.IN_APP, message));

        assertTrue(ex.getDisplayMessage().contains("站内信通道尚未实现"));
        verify(smsChannelPort, never()).send(any());
        verify(emailChannelPort, never()).send(any());
    }

    @Test
    @DisplayName("通道选择 — 消息类型与通道不匹配 → 参数错误，且不触发任何投递")
    void shouldThrowParamError_whenPayloadTypeMismatch() {
        EmailMessage emailPayload = new EmailMessage("a@b.com", "主题", "正文", "req-3", "LOGIN");

        BizException ex = assertThrows(BizException.class,
                () -> dispatcher.dispatch(NotificationChannel.SMS, emailPayload));

        assertEquals("参数错误", ex.getErrorCode().getMessage());
        verify(smsChannelPort, never()).send(any());
        verify(emailChannelPort, never()).send(any());
    }

    @Test
    @DisplayName("通道解析 — 小写/前后空格编码可正确解析，非法编码抛参数错误")
    void shouldResolveChannel_whenCodeIgnoringCaseAndBlank() {
        assertEquals(NotificationChannel.SMS, NotificationChannel.of(" sms "));
        assertEquals(NotificationChannel.EMAIL, NotificationChannel.of("email"));
        assertThrows(BizException.class, () -> NotificationChannel.of("PUSH"));
        assertThrows(BizException.class, () -> NotificationChannel.of(null));
    }
}
