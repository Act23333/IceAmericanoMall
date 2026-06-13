package org.noLazy.common.client.sms.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.noLazy.common.client.sms.SmsClient;
import org.noLazy.common.exception.BizException;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.utils.AliyunSmsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Aliyun SMS client — only activated when {@code aliyun.sms.enabled=true}.
 * Requires {@code aliyun.sms.*} properties and the Aliyun SMS SDK on the classpath.
 * For local development the {@link MockSmsClient} is used instead.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aliyun.sms.enabled", havingValue = "true")
public class AliyunSmsClient implements SmsClient {

    private final AliyunSmsProperties properties;

    @Override
    public void send(String phone, String code) {
        // TODO: Integrate Aliyun SMS SDK when credentials are available.
        // For now, log the code and throw a clear error so the developer knows to configure Aliyun
        // or use the MockSmsClient (default).
        log.warn("Aliyun SMS is enabled but SDK integration is pending. Phone: {}, Code: {}", phone, code);
        throw new BizException(ErrorCode.INTERNAL_ERROR, "Aliyun SMS SDK not yet integrated. Disable aliyun.sms.enabled for local dev.");
    }
}
