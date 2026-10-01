package org.icedamericanomall.notification.app.command;

import org.icedamericanomall.notification.domain.model.SmsMessage;

/**
 * 短信发送命令（应用层入参）。
 *
 * <p>由适配层从强类型 Feign DTO（{@code NotificationSendReqCO}）转换而来，
 * 应用层与领域层不依赖 ia-api 契约类。
 *
 * @param phone     接收手机号
 * @param content   短信正文
 * @param requestId 幂等 / 链路追踪标识
 * @param scene     业务场景
 */
public record SmsSendCommand(String phone, String content, String requestId, String scene) {

    /** 转换为领域消息（领域对象负责自校验）。 */
    public SmsMessage toMessage() {
        return new SmsMessage(phone, content, requestId, scene);
    }
}
