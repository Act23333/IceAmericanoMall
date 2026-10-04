package org.icedamericanomall.notification.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.integration.email.EmailClient;
import org.icedamericanomall.notification.domain.model.channel.EmailMessage;
import org.icedamericanomall.notification.domain.port.EmailChannelPort;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.stereotype.Component;

/**
 * 邮件通道适配器（基础设施层）—— 领域端口 → ia-integration 的 {@code EmailClient}。
 *
 * <p>实现方（SMTP / Mock）由 {@code notification.email.enabled} 切换，本类不感知。
 * 领域对象 {@link EmailMessage} 与集成层同名对象
 * {@code org.icedamericanomall.integration.email.EmailMessage} 在此显式转换
 * （同名不同层，转换代码即防腐层边界）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailChannelAdapter implements EmailChannelPort {

    private final EmailClient emailClient;

    @Override
    public void send(EmailMessage message) {
        try {
            emailClient.send(new org.icedamericanomall.integration.email.EmailMessage(
                    message.to(), message.subject(), message.content()));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("邮件通道投递异常 requestId={}", message.requestId(), e);
            throw new BizException(ErrorCode.EMAIL_SEND_FAILED, "邮件通道投递失败", e);
        }
    }
}
