package org.icedamericanomall.integration.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock 邮件客户端 —— 本地开发 / 测试默认激活（{@code notification.email.enabled} 缺省或为 false），
 * 只把邮件内容打到日志，不产生真实外呼。
 *
 * <p>与 {@code sms/MockSmsClient} 的差异：此处不加 {@code @Primary}。
 * Mock / Real 通过 {@code havingValue + matchIfMissing} 互斥，
 * 避免 {@code @Primary} 永久遮蔽真实实现 —— {@code @Primary} 会把「互斥失效」
 * 这一本应 fail-fast 的配置错误，静默降级成「Mock 悄悄胜出」。
 * 该约定现已推广到全部五组 Mock 实现（V5.2 已清理 sms / wechat / storage 中残留的 {@code @Primary}）。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "notification.email.enabled", havingValue = "false", matchIfMissing = true)
public class MockEmailClient implements EmailClient {

    @Override
    public void send(EmailMessage message) {
        log.info("===== MOCK EMAIL =====");
        log.info("To: {}", message.to());
        log.info("Subject: {}", message.subject());
        log.info("Content: {}", message.content());
        log.info("===== END MOCK EMAIL =====");
    }
}
