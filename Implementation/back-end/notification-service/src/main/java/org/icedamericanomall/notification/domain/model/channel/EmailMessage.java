package org.icedamericanomall.notification.domain.model.channel;

/**
 * 邮件内容（领域值对象，不可变 + 创建即校验）。
 *
 * <p>通道适配器负责把本对象翻译成 ia-integration 的 {@code EmailClient.send(EmailMessage)} 调用。
 *
 * @param to        收件人邮箱
 * @param subject   邮件主题
 * @param content   邮件正文（纯文本，调用方已完成渲染）
 * @param requestId 幂等 / 链路追踪标识（透传，可为空）
 * @param scene     业务场景（LOGIN / REGISTER ...）
 */
public record EmailMessage(String to, String subject, String content, String requestId, String scene) {

    /** 紧凑构造器：创建即校验（含最基础的邮箱形态校验，避免把明显非法值投递到 SMTP）。 */
    public EmailMessage {
        if (to == null || to.isBlank() || !to.contains("@")) {
            throw new IllegalArgumentException("邮件收件人格式非法");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("邮件主题不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("邮件正文不能为空");
        }
    }
}
