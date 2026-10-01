package org.icedamericanomall.integration.email;

/**
 * 邮件发送客户端接口（第三方集成层）。
 *
 * <p>设计对齐 {@code sms/SmsClient}：接口只暴露「一次投递」的最小语义，
 * Mock/Real 实现由 {@code notification.email.enabled} 属性切换：
 *
 * <ul>
 *   <li>{@code false}（默认，dev）→ {@link MockEmailClient} 日志投递</li>
 *   <li>{@code true} → {@link RealEmailClient} 走 SMTP 真实投递</li>
 * </ul>
 *
 * <p>调用方（notification-service）只依赖本接口，不感知具体实现与厂商。
 */
public interface EmailClient {

    /**
     * 投递一封邮件。
     *
     * <p>语义约定：投递失败必须抛出异常，不允许静默吞掉 —— 上游
     * notification-service 的 {@code /internal/**} 端点会把异常原样透传给 Feign 调用方，
     * 由调用方决定重试或降级。
     *
     * @param message 邮件内容（收件人 / 主题 / 正文），不可为 {@code null}
     * @throws org.icedamericanomall.common.exception.BizException 投递失败（ErrorCode.EMAIL_SEND_FAILED）
     */
    void send(EmailMessage message);
}
