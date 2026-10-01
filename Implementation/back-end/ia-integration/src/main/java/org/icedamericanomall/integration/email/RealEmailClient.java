package org.icedamericanomall.integration.email;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * 真实邮件客户端（SMTP）—— {@code notification.email.enabled=true} 且类路径存在
 * {@code spring-boot-starter-mail} 时激活。
 *
 * <p>不使用 Spring Boot 的 {@code spring.mail.*} 自动配置，而是直接由
 * {@link EmailProperties}（{@code notification.email.*}）构建 {@link JavaMailSenderImpl}：
 * 与 {@code AliyunSmsClient} 自行构建 SDK {@code Client} 的风格保持一致，
 * 避免同一份配置出现两个命名空间。
 *
 * <pre>
 * notification:
 *   email:
 *     enabled: true
 *     host: smtp.exmail.qq.com
 *     port: 465
 *     username: no-reply@example.com
 *     password: ${MAIL_PASSWORD}
 *     from: 冰美商城 &lt;no-reply@example.com&gt;
 * </pre>
 *
 * <h2>健壮性约定（V5.2 补齐）</h2>
 * <ul>
 *   <li><b>超时</b>：建连 / 读 / 写三个超时全部显式设置，避免 SMTP 抖动时线程被无限期占用
 *       （Spring Mail 默认无超时）。</li>
 *   <li><b>TLS</b>：465 隐式 SSL 与 587 STARTTLS 两条路径都支持；
 *       开启 STARTTLS 同时设置 {@code starttls.required=true}，拒绝明文降级；
 *       并默认开启 {@code ssl.checkserveridentity}（JavaMail 默认关闭，存在中间人风险）。</li>
 *   <li><b>配置缺漏不静默</b>：构造期即对「配置不完整」「端口与加密方式矛盾」打 WARN，
 *       发送期再硬失败（{@code BizException}），避免把坏配置拖到线上才暴露。</li>
 *   <li><b>异常语义</b>：所有投递失败统一抛 {@code BizException(EMAIL_SEND_FAILED)}，
 *       保证 notification-service 的 {@code /internal/**} 能把失败透传给调用方。</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnClass(JavaMailSenderImpl.class)
@ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true")
public class RealEmailClient implements EmailClient {

    private final EmailProperties properties;
    private final JavaMailSenderImpl mailSender;

    public RealEmailClient(EmailProperties properties) {
        this.properties = properties;
        this.mailSender = buildMailSender(properties);
    }

    @Override
    public void send(EmailMessage message) {
        if (!properties.isConfigured()) {
            log.error("邮件配置不完整(host/from 缺失)，拒绝投递 to={}", message.to());
            throw new BizException(ErrorCode.EMAIL_SEND_FAILED, "邮件服务未配置，无法发送");
        }
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(properties.getFrom());
            mail.setTo(message.to());
            mail.setSubject(message.subject());
            mail.setText(message.content());
            mailSender.send(mail);
            log.info("邮件发送成功 to={}, subject={}", message.to(), message.subject());
        } catch (MailException e) {
            log.error("邮件发送失败 to={}, subject={}", message.to(), message.subject(), e);
            throw new BizException(ErrorCode.EMAIL_SEND_FAILED, "邮件发送失败", e);
        }
    }

    /** 由配置构建 SMTP 发送器（构造期完成，发送时不重复建连配置）。 */
    private static JavaMailSenderImpl buildMailSender(EmailProperties props) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(props.getHost());
        sender.setPort(props.getPort());
        sender.setUsername(props.getUsername() != null ? props.getUsername() : props.getFrom());
        sender.setPassword(props.getPassword());
        sender.setDefaultEncoding(props.getDefaultEncoding());

        Properties javaMailProperties = sender.getJavaMailProperties();
        javaMailProperties.put("mail.transport.protocol", props.getProtocol());
        javaMailProperties.put("mail.smtp.auth", "true");
        javaMailProperties.put("mail.smtp.ssl.enable", String.valueOf(props.isSslEnabled()));
        javaMailProperties.put("mail.smtp.connectiontimeout", String.valueOf(props.getConnectTimeout()));
        javaMailProperties.put("mail.smtp.timeout", String.valueOf(props.getTimeout()));
        javaMailProperties.put("mail.smtp.writetimeout", String.valueOf(props.getTimeout()));
        // V5.2: STARTTLS（587 提交端口）+ 拒绝明文降级
        javaMailProperties.put("mail.smtp.starttls.enable", String.valueOf(props.isStarttlsEnabled()));
        javaMailProperties.put("mail.smtp.starttls.required", String.valueOf(props.isStarttlsEnabled()));
        // V5.2: 证书主机名校验（JavaMail 默认 false，等于放弃中间人防护）
        javaMailProperties.put("mail.smtp.ssl.checkserveridentity",
                String.valueOf(props.isCheckServerIdentity()));

        warnOnSuspiciousConfig(props);
        log.info("邮件客户端初始化完成 host={}, port={}, ssl={}, starttls={}",
                props.getHost(), props.getPort(), props.isSslEnabled(), props.isStarttlsEnabled());
        return sender;
    }

    /**
     * 启动期一致性告警 —— 只告警不拦截：SMTP 服务端配置千差万别，硬校验可能误伤可用配置，
     * 但这两类矛盾是「连不上 / 投不出去」的高频根因，提前吵一声比线上排查便宜。
     */
    private static void warnOnSuspiciousConfig(EmailProperties props) {
        if (!props.isConfigured()) {
            log.warn("邮件配置不完整（host/from 缺失），notification.email.enabled=true 时投递会直接失败");
        }
        String mismatch = props.describeTlsPortMismatch();
        if (mismatch != null) {
            log.warn("邮件 TLS 配置可疑：{}（host={}, port={}, ssl={}, starttls={}）",
                    mismatch, props.getHost(), props.getPort(),
                    props.isSslEnabled(), props.isStarttlsEnabled());
        }
        if (props.isSslEnabled() && !props.isCheckServerIdentity()) {
            log.warn("邮件已关闭服务端证书主机名校验（mail.smtp.ssl.checkserveridentity=false），"
                    + "存在中间人风险，请确认仅用于自签名证书的内网 SMTP");
        }
    }
}
