package org.icedamericanomall.notification.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.utils.crypto.DesensitizeUtil;
import org.icedamericanomall.notification.app.command.EmailSendCommand;
import org.icedamericanomall.notification.app.command.SmsSendCommand;
import org.icedamericanomall.notification.domain.service.NotificationDispatcher;
import org.springframework.stereotype.Component;

/**
 * 渠道投递用例编排器（应用层）—— 短信 / 邮件两个投递通道。
 *
 * <p>职责：记录带 requestId 的投递日志 → 组装领域消息 → 交给领域层分发。
 * 业务规则（通道可用性、消息自校验）在领域层，第三方 SDK 在基础设施层，
 * 本类只做顺序编排与日志埋点，<b>不写业务规则</b>。
 *
 * <p><b>拆分说明（结构收口）</b>：原 {@code NotificationManager} 同时承载「渠道投递」与
 * 「站内信管理」两个互不相关的用例族，且各自依赖不同的协作者（{@link NotificationDispatcher}
 * vs {@code InAppMessageRepository}）；现按用例族拆为
 * {@code ChannelDispatchManager}（本类，通道投递）与 {@code InAppMessageManager}（站内信），
 * HTTP 契约不变。
 *
 * <p>幂等说明：通道投递本身无状态，重复调用不会破坏数据；
 * {@code requestId} 由调用方提供并原样记录，用于链路排查与上游去重。
 * 未来若引入发送审计表，应在此处以 {@code requestId} 做唯一键。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelDispatchManager {

    private final NotificationDispatcher notificationDispatcher;

    /**
     * 发送短信。
     *
     * @param command 短信发送命令
     * @throws org.icedamericanomall.common.exception.BizException 参数非法或投递失败
     */
    public void sendSms(SmsSendCommand command) {
        long start = System.currentTimeMillis();
        log.info("[notify][sms][start] requestId={}, scene={}, target={}",
                command.requestId(), command.scene(), DesensitizeUtil.maskPhone(command.phone()));
        notificationDispatcher.sendSms(command.toMessage());
        log.info("[notify][sms][ok] requestId={}, scene={}, cost={}ms",
                command.requestId(), command.scene(), System.currentTimeMillis() - start);
    }

    /**
     * 发送邮件。
     *
     * @param command 邮件发送命令
     * @throws org.icedamericanomall.common.exception.BizException 参数非法或投递失败
     */
    public void sendEmail(EmailSendCommand command) {
        long start = System.currentTimeMillis();
        log.info("[notify][email][start] requestId={}, scene={}, target={}, subject={}",
                command.requestId(), command.scene(),
                DesensitizeUtil.maskEmail(command.to()), command.subject());
        notificationDispatcher.sendEmail(command.toMessage());
        log.info("[notify][email][ok] requestId={}, scene={}, cost={}ms",
                command.requestId(), command.scene(), System.currentTimeMillis() - start);
    }
}
