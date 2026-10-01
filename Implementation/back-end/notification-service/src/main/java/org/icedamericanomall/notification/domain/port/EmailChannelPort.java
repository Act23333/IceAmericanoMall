package org.icedamericanomall.notification.domain.port;

import org.icedamericanomall.notification.domain.model.EmailMessage;

/**
 * 邮件投递能力（领域端口，基础设施层用 ia-integration 的 {@code EmailClient} 实现）。
 */
public interface EmailChannelPort {

    /**
     * 投递邮件。
     *
     * @param message 邮件内容
     * @throws org.icedamericanomall.common.exception.BizException 投递失败（ErrorCode.EMAIL_SEND_FAILED）
     */
    void send(EmailMessage message);
}
