package org.icedamericanomall.notification.adapter.internal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.dto.InAppMessageSendReqCO;
import org.icedamericanomall.dto.NotificationSendReqCO;
import org.icedamericanomall.notification.app.ChannelDispatchManager;
import org.icedamericanomall.notification.app.InAppMessageManager;
import org.icedamericanomall.notification.app.command.EmailSendCommand;
import org.icedamericanomall.notification.app.command.SmsSendCommand;
import org.icedamericanomall.notification.domain.model.inapp.InAppMessage;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通信服务内部端点（适配层）—— 仅集群内 Feign 调用，Gateway 拦截外网访问。
 *
 * <p>端点清单（与 ia-api {@code NotificationClient} 严格一一对应）：
 * <pre>
 * POST /internal/notify/sms     短信通道投递
 * POST /internal/notify/email   邮件通道投递
 * POST /internal/notify/in-app  站内信（落库，self-send 忽略）
 * </pre>
 *
 * <p>参数校验：请求体使用 ia-api 强类型 DTO，跨字段规则（如邮件主题必填）由领域对象承担。
 *
 * <p>异常约定：{@code /internal/**} 不包装 {@code Result}，
 * {@code GlobalExceptionHandler} 会把业务异常原样透传，Feign 调用方直接拿到失败。
 */
@Slf4j
@RestController
@RequestMapping("/internal/notify")
@RequiredArgsConstructor
public class InternalNotifyController {

    private final ChannelDispatchManager channelDispatchManager;
    private final InAppMessageManager inAppMessageManager;

    /** 短信通道投递。 */
    @PostMapping("/sms")
    public void sendSms(@Valid @RequestBody NotificationSendReqCO request) {
        channelDispatchManager.sendSms(new SmsSendCommand(
                request.getTarget(), request.getContent(), request.getRequestId(), request.getScene()));
    }

    /** 邮件通道投递。 */
    @PostMapping("/email")
    public void sendEmail(@Valid @RequestBody NotificationSendReqCO request) {
        channelDispatchManager.sendEmail(new EmailSendCommand(
                request.getTarget(), request.getSubject(), request.getContent(),
                request.getRequestId(), request.getScene()));
    }

    /** 站内信投递（落库；self-send 忽略）。 */
    @PostMapping("/in-app")
    public void sendInAppMessage(@Valid @RequestBody InAppMessageSendReqCO request) {
        inAppMessageManager.sendInAppMessage(InAppMessage.unread(
                request.getRecipientId(), request.getSenderId(), request.getSenderName(),
                request.getSenderAvatar(), request.getType(), request.getTitle(),
                request.getContent(), request.getLinkUrl()));
    }
}
