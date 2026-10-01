package org.icedamericanomall.integration.email;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 邮件（SMTP）配置。命名空间 {@code notification.email}，与
 * {@code aliyun.sms} / {@code geetest} 同级，由 notification-service 的 application.yml 提供。
 *
 * <pre>
 * notification:
 *   email:
 *     enabled: false              # false=Mock（日志投递），true=真实 SMTP
 *     host: smtp.exmail.qq.com
 *     port: 465
 *     username: no-reply@example.com
 *     password: ${MAIL_PASSWORD}
 *     from: 冰美商城 &lt;no-reply@example.com&gt;
 *     ssl-enabled: true
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "notification.email")
public class EmailProperties {

    /** 是否启用真实 SMTP 投递（false 时使用 MockEmailClient） */
    private boolean enabled = false;

    /** SMTP 服务器地址 */
    private String host;

    /** SMTP 端口（465=SSL, 587=STARTTLS, 25=明文） */
    private int port = 465;

    /** 认证用户名，缺省时使用 {@link #from} 中的邮箱地址 */
    private String username;

    /** 认证密码 / 授权码（生产环境走环境变量注入，禁止硬编码） */
    private String password;

    /** 发件人展示地址，如 {@code 冰美商城 <no-reply@example.com>} */
    private String from;

    /** 传输协议，默认 smtp */
    private String protocol = "smtp";

    /** 是否开启 SSL 直连（465 端口需为 true） */
    private boolean sslEnabled = true;

    /**
     * 是否启用 STARTTLS（V5.2 新增）。587 端口（提交端口）应为 {@code true}：
     * 先明文握手再升级 TLS，与 465 的「隐式 SSL」互斥。
     *
     * <p>默认 {@code false} 以保持与改造前一致的行为；开启后会同时设置
     * {@code mail.smtp.starttls.required=true}，拒绝降级回明文（防降级攻击）。
     */
    private boolean starttlsEnabled = false;

    /**
     * 是否校验服务端证书主机名（V5.2 新增）。
     *
     * <p>JavaMail 默认<b>不校验</b>（{@code mail.smtp.ssl.checkserveridentity=false}），
     * 意味着任何持有受信任 CA 签发证书的中间人都能解密邮件。
     * 本项目默认 {@code true}（安全优先），仅在自签名证书的内网 SMTP 上才应关闭。
     */
    private boolean checkServerIdentity = true;

    /** 默认编码 */
    private String defaultEncoding = "UTF-8";

    /** 建连超时（毫秒） */
    private int connectTimeout = 5000;

    /** 读写超时（毫秒） */
    private int timeout = 5000;

    /** 判断配置是否完整（host 与 from 必填），不完整时真实投递直接失败而非静默丢弃。 */
    public boolean isConfigured() {
        return host != null && !host.isBlank() && from != null && !from.isBlank();
    }

    /**
     * 端口 / 加密方式是否自相矛盾 —— 常见的「连不上」根因，仅用于启动告警不做拦截。
     *
     * <ul>
     *   <li>465 是隐式 SSL 端口，不应再开 STARTTLS；</li>
     *   <li>587 是提交端口，应开 STARTTLS 而不是直接 SSL。</li>
     * </ul>
     *
     * @return 矛盾描述；无矛盾时返回 {@code null}
     */
    public String describeTlsPortMismatch() {
        if (port == 465 && starttlsEnabled) {
            return "port=465 为隐式 SSL 端口，不应同时开启 starttls-enabled";
        }
        if (port == 587 && !starttlsEnabled) {
            return "port=587 为提交端口，通常需要 starttls-enabled=true";
        }
        return null;
    }
}
