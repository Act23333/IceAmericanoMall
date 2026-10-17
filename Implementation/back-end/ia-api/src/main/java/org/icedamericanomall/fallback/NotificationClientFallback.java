package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.NotificationClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.InAppMessageSendReqCO;
import org.icedamericanomall.dto.NotificationSendReqCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 通信服务降级工厂 —— <b>下游 4xx 透传 / 其余保持 fail-closed（抛异常）</b>。
 *
 * <p><b>降级语义：不假装成功。</b>通道投递失败若被静默吞掉，调用方会误以为
 * 「短信/邮件已发出」，用户则会一直等待永远收不到的消息（验证码场景会直接卡死登录）。
 * 因此降级路径统一抛出 ia-common 业务异常，由调用方决定重试 / 提示 / 换通道。
 *
 * <p><b>分支（{@code 09-constraints} §三·八·1 透传优先）</b>：
 * <ul>
 *   <li>{@code category == CLIENT}（下游 4xx + 已知 {@code code}，如目标号段非法 / 报文不合规）→
 *       <b>原样抛 {@link UpstreamException}</b>，保留下游 {@code code} / 文案 / {@code traceId}——
 *       那是<b>业务事实</b>，说成「通知服务不可用」会误导调用方去做必然失败的重试；</li>
 *   <li>{@code TRANSIENT} / {@code BUG}（不可用 / 超时 / 熔断 / 下游自身缺陷）→
 *       <b>原有降级口径一字不变</b>：{@code SMS_SEND_FAILED} / {@code EMAIL_SEND_FAILED} /
 *       {@code BUSINESS_EXECUTION_EXCEPTION}。</li>
 * </ul>
 *
 * <p><b>透传 4xx 不会让调用方误判（已核实）</b>：通知是 best-effort 旁路 ——
 * {@code TradeEventNotifier} / {@code PayEventNotifier} / {@code StoreFollowManager} /
 * {@code ReviewManager} 均以 {@code catch (Exception)} 包住调用、只记告警不外溢；
 * {@code VerificationCodeIssuer#issue} 同样 {@code catch (RuntimeException)} → 回滚验证码与限流后
 * 返回 {@code SEND_FAILED}。故两条分支都<b>不存在</b>「静默当成已发送」的路径。
 */
@Slf4j
@Component
public class NotificationClientFallback implements FallbackFactory<NotificationClient> {

    @Override
    public NotificationClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        return new NotificationClient() {

            @Override
            public void sendSms(NotificationSendReqCO request) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("短信通道降级(透传): 下游业务失败 target={}, scene={}, code={}, msg={}",
                            request == null ? "***" : maskTarget(request),
                            request == null ? null : request.getScene(),
                            upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("短信通道降级 target={}, scene={}", maskTarget(request), request.getScene(), cause);
                throw new BizException(ErrorCode.SMS_SEND_FAILED, "通知服务不可用，短信发送失败");
            }

            @Override
            public void sendEmail(NotificationSendReqCO request) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("邮件通道降级(透传): 下游业务失败 target={}, scene={}, code={}, msg={}",
                            request == null ? "***" : maskTarget(request),
                            request == null ? null : request.getScene(),
                            upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("邮件通道降级 target={}, scene={}", maskTarget(request), request.getScene(), cause);
                throw new BizException(ErrorCode.EMAIL_SEND_FAILED, "通知服务不可用，邮件发送失败");
            }

            @Override
            public void sendInAppMessage(InAppMessageSendReqCO request) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("站内信通道降级(透传): 下游业务失败 recipientId={}, type={}, code={}, msg={}",
                            request == null ? null : request.getRecipientId(),
                            request == null ? null : request.getType(),
                            upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
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
