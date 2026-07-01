package org.icedAmericanoMall.integration.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Mock 短信客户端 — 本地开发默认激活，将验证码打印到日志。
 * 生产环境通过 {@code aliyun.sms.enabled=true} 切换到 AliyunSmsClient。
 */
@Slf4j
@Component
@Primary
public class MockSmsClient implements SmsClient {

    @Override
    public void send(String phone, String code) {
        log.info("===== MOCK SMS =====");
        log.info("To: {}", phone);
        log.info("Code: {}", code);
        log.info("===== END MOCK SMS =====");
    }
}
