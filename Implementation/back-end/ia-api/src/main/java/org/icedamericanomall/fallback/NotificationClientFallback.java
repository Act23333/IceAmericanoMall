package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.NotificationClient;
import org.icedamericanomall.dto.InAppMessageSendReqCO;
import org.icedamericanomall.dto.NotificationSendReqCO;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 通信服务降级工厂。
 *
 * <p><b>降级语义：不假装成功。</b>通道投递失败若被静默吞掉，调用方会误以为
 * 「短信/邮件已发出」，用户则会一直等待永远收不到的消息（验证码场景会直接卡死登录）。
 * 因此降级路径统一抛出 ia-common 业务异常，由调用方决定重试 / 提示 / 换通道。
 */
@Slf4j
@Component
public class NotificationClientFallback implements FallbackFactory<NotificationClient> {

    @Override
    public NotificationClient create(Throwable cause) {
        return new NotificationClient() {

            @Override
            public void sendSms(NotificationSendReqCO request) {
                log.error("短信通道降级 target={}, scene={}", maskTarget(request), request.getScene(), cause);
                throw new BizException(ErrorCode.SMS_SEND_FAILED, "通知服务不可用，短信发送失败");
            }

            @Override
            public void sendEmail(NotificationSendReqCO request) {
                log.error("邮件通道降级 target={}, scene={}", maskTarget(request), request.getScene(), cause);
                throw new BizException(ErrorCode.EMAIL_SEND_FAILED, "通知服务不可用，邮件发送失败");
            }

            @Override
            public void sendInAppMessage(InAppMessageSendReqCO request) {
                log.error("站内信通道降级 recipientId={}, type={}", request.getRecipientId(), request.getType(), cause);
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "站内信通道不可用");
            }
        };
    }

    /** 日志脱敏：只保留目标末 4 位（09-constraints：日志禁止打印完整手机号/邮箱）。 */
    private static String maskTarget(NotificationSendReqCO request) {
        String target = request.getTarget();
        if (target == null || target.length() <= 4) {
            return "***";
        }
        return "***" + target.substring(target.length() - 4);
    }
}
