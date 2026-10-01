package org.icedamericanomall.integration.email;

/**
 * 待投递邮件（不可变值对象，自封装校验）。
 *
 * <p>校验分两类：
 * <ol>
 *   <li><b>必填</b> —— 收件人 / 主题 / 正文不得为空（否则投递必然失败或用户收到空邮件）。</li>
 *   <li><b>CRLF 注入防护</b>（V5.2 新增）—— 收件人与主题中出现 {@code CR}/{@code LF}
 *       属于 SMTP 头注入（email header injection）攻击面：JavaMail 的
 *       {@code MimeMessage} 不会自动清洗头字段，攻击者可通过
 *       {@code "a@b.com\r\nBcc: victim@evil.com"} 暗送邮件。
 *       {@code content} 是正文（不受头注入影响）故不限制换行。</li>
 * </ol>
 *
 * <p>抛 {@link IllegalArgumentException} 而非业务异常：本类是集成层的输入契约守卫，
 * 违规只可能来自上游编码缺陷（调用点 {@code EmailChannelAdapter} 已统一兜底转换为
 * {@code BizException(EMAIL_SEND_FAILED)}），不需要携带业务错误码。
 *
 * @param to      收件人邮箱（禁止包含 CR/LF）
 * @param subject 邮件主题（禁止包含 CR/LF）
 * @param content 邮件正文（纯文本）
 */
public record EmailMessage(String to, String subject, String content) {

    /** 紧凑构造器：创建即校验，杜绝非法邮件进入投递链路。 */
    public EmailMessage {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("邮件收件人不能为空");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("邮件主题不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("邮件正文不能为空");
        }
        if (containsCrLf(to)) {
            throw new IllegalArgumentException("邮件收件人不能包含换行符（SMTP 头注入防护）");
        }
        if (containsCrLf(subject)) {
            throw new IllegalArgumentException("邮件主题不能包含换行符（SMTP 头注入防护）");
        }
    }

    /** 是否包含 CR/LF —— SMTP 头字段的换行即头注入。 */
    private static boolean containsCrLf(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }
}
