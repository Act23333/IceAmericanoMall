package org.icedamericanomall.notification.app.command;

import org.icedamericanomall.notification.domain.model.EmailMessage;

/**
 * 邮件发送命令（应用层入参）。
 *
 * @param to        收件人邮箱
 * @param subject   邮件主题
 * @param content   邮件正文
 * @param requestId 幂等 / 链路追踪标识
 * @param scene     业务场景
 */
public record EmailSendCommand(String to, String subject, String content, String requestId, String scene) {

    /** 转换为领域消息（领域对象负责自校验）。 */
    public EmailMessage toMessage() {
        return new EmailMessage(to, subject, content, requestId, scene);
    }
}
