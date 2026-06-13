package org.noLazy.common.client.sms.impl;

import lombok.extern.slf4j.Slf4j;
import org.noLazy.common.client.sms.SmsClient;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Mock SMS client for local development — logs verification codes instead of sending real SMS.
 * Activated by default; real AliyunSmsClient requires {@code aliyun.sms.enabled=true}.
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
