package org.icedamericanomall.client;

import org.icedamericanomall.dto.InAppMessageSendReqCO;
import org.icedamericanomall.dto.NotificationSendReqCO;
import org.icedamericanomall.fallback.NotificationClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 通信服务 Feign 客户端 —— 各服务 → notification-service（纯通道发送）。
 *
 * <p>职责边界：notification-service 只负责「选通道 + 投递」，
 * <b>不生成验证码、不做模板编排、不落库</b>；验证码签发/校验在 verification-service。
 *
 * <p>调用链（V5.1 目标形态）：
 * <pre>
 * authorization-service / user-service
 *     → (Feign) verification-service  （签发/校验验证码）
 *     → (Feign) notification-service  （SMS / EMAIL 通道投递）
 *     → ia-integration                （阿里云短信 / SMTP）
 * </pre>
 *
 * <p>端点与请求体强类型一一对应：{@code /sms}、{@code /email}、{@code /in-app}（站内信契约预留）。
 * 通道由路径决定，请求体不携带 channel 字段，避免 body 与路径相矛盾。
 */
@FeignClient(name = "notification-service", path = "/internal/notify", contextId = "notification",
        fallbackFactory = NotificationClientFallback.class)
public interface NotificationClient {

    /**
     * 短信通道投递（阿里云短信 / Mock）。
     *
     * @param request 发送请求（target=手机号）
     * @throws org.icedamericanomall.common.exception.BizException 投递失败（ErrorCode.SMS_SEND_FAILED）
     */
    @PostMapping("/sms")
    void sendSms(@RequestBody NotificationSendReqCO request);

    /**
     * 邮件通道投递（SMTP / Mock）。
     *
     * @param request 发送请求（target=邮箱，subject 必填）
     * @throws org.icedamericanomall.common.exception.BizException 投递失败（ErrorCode.EMAIL_SEND_FAILED）
     */
    @PostMapping("/email")
    void sendEmail(@RequestBody NotificationSendReqCO request);

    /**
     * 站内信通道投递 —— <b>契约预留，尚未实现</b>（V5.0 规划）。
     *
     * <p>调用方必须按「可失败的旁路通知」处理：捕获异常并记录日志，不得影响主流程。
     *
     * @param request 站内信内容
     * @throws org.icedamericanomall.common.exception.BizException 通道未实现 / 服务不可用
     */
    @PostMapping("/in-app")
    void sendInAppMessage(@RequestBody InAppMessageSendReqCO request);
}
