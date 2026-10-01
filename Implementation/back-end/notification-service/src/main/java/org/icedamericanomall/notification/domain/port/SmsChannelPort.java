package org.icedamericanomall.notification.domain.port;

import org.icedamericanomall.notification.domain.model.SmsMessage;

/**
 * 短信投递能力（领域端口，基础设施层用 ia-integration 的 {@code SmsClient} 实现）。
 */
public interface SmsChannelPort {

    /**
     * 投递短信。
     *
     * @param message 短信内容
     * @throws org.icedamericanomall.common.exception.BizException 投递失败（ErrorCode.SMS_SEND_FAILED）
     */
    void send(SmsMessage message);
}
