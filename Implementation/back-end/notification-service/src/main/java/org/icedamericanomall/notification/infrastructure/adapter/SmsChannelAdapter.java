package org.icedamericanomall.notification.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.integration.sms.SmsClient;
import org.icedamericanomall.notification.domain.model.channel.SmsMessage;
import org.icedamericanomall.notification.domain.port.SmsChannelPort;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.stereotype.Component;

/**
 * 短信通道适配器（基础设施层）—— 领域端口 → ia-integration 的 {@code SmsClient}。
 *
 * <p>实现方（阿里云 dysmsapi / Mock）由 {@code aliyun.sms.enabled} 切换，本类不感知。
 *
 * <p><b>已知限制</b>：当前 {@code SmsClient} 只支持把正文塞进模板变量 {@code code}
 * （见 {@code AliyunSmsClient}），因此短信模板变量名固定为 {@code code}；
 * 模板参数化改造（{@code Map<String,String> templateParams}）列入 TODO，属 ia-integration 范围。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsChannelAdapter implements SmsChannelPort {

    private final SmsClient smsClient;

    @Override
    public void send(SmsMessage message) {
        try {
            smsClient.send(message.phone(), message.content());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("短信通道投递异常 requestId={}", message.requestId(), e);
            throw new BizException(ErrorCode.SMS_SEND_FAILED, "短信通道投递失败", e);
        }
    }
}
