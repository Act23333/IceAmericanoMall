package org.icedamericanomall.notification.domain;

import org.icedamericanomall.notification.domain.model.EmailMessage;
import org.icedamericanomall.notification.domain.model.SmsMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 通道消息值对象测试 —— 创建即校验（自封装），不依赖任何框架。
 */
@DisplayName("通信 — 通道消息值对象自校验")
class NotificationMessageTest {

    @Test
    @DisplayName("短信消息 — 号码/正文为空或超长 → 拒绝创建")
    void shouldRejectSmsMessage_whenPhoneOrContentInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> new SmsMessage("  ", "验证码 123456", "req-1", "LOGIN"));
        assertThrows(IllegalArgumentException.class,
                () -> new SmsMessage("13800001111", "", "req-1", "LOGIN"));
        assertThrows(IllegalArgumentException.class,
                () -> new SmsMessage("1".repeat(33), "验证码 123456", "req-1", "LOGIN"));
        assertDoesNotThrow(() -> new SmsMessage("13800001111", "验证码 123456", null, null));
    }

    @Test
    @DisplayName("邮件消息 — 收件人无 @ / 主题空 / 正文空 → 拒绝创建")
    void shouldRejectEmailMessage_whenFieldInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> new EmailMessage("not-an-email", "主题", "正文", "req-2", "LOGIN"));
        assertThrows(IllegalArgumentException.class,
                () -> new EmailMessage("a@b.com", " ", "正文", "req-2", "LOGIN"));
        assertThrows(IllegalArgumentException.class,
                () -> new EmailMessage("a@b.com", "主题", null, "req-2", "LOGIN"));
        EmailMessage message = new EmailMessage("a@b.com", "登录验证码", "验证码 123456", "req-2", "LOGIN");
        assertEquals("EMAIL", message.channelCode());
    }
}
