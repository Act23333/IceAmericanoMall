package org.noLazy.common.client.sms.impl;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.teaopenapi.models.Config;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.noLazy.common.client.sms.SmsClient;
import org.noLazy.common.utils.AliyunSmsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Aliyun SMS client — only activated when {@code aliyun.sms.enabled=true}.
 * Uses the official Aliyun dysmsapi20170525 SDK.
 * For local development {@link MockSmsClient} is used instead (default).
 *
 * <pre>
 * Required configuration (application.yml):
 *   aliyun.sms:
 *     enabled: true
 *     access-key-id: your-key
 *     access-key-secret: your-secret
 *     sign-name: 冰美商城
 *     template-code: SMS_123456789
 * </pre>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "aliyun.sms.enabled", havingValue = "true")
public class AliyunSmsClient implements SmsClient {

    private final AliyunSmsProperties properties;
    private Client client;

    public AliyunSmsClient(AliyunSmsProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        try {
            Config config = new Config()
                    .setAccessKeyId(properties.getAccessKeyId())
                    .setAccessKeySecret(properties.getAccessKeySecret());
            config.endpoint = "dysmsapi.aliyuncs.com";
            this.client = new Client(config);
            log.info("阿里云短信客户端初始化成功");
        } catch (Exception e) {
            log.error("阿里云短信客户端初始化失败", e);
        }
    }

    @Override
    public void send(String phone, String code) {
        if (client == null) {
            log.error("阿里云短信客户端未初始化，无法发送短信 phone={}", phone);
            return;
        }
        try {
            SendSmsRequest request = new SendSmsRequest()
                    .setPhoneNumbers(phone)
                    .setSignName(properties.getSignName())
                    .setTemplateCode(properties.getTemplateCode())
                    .setTemplateParam("{\"code\":\"" + code + "\"}");
            client.sendSms(request);
            log.info("短信发送成功 phone={}", phone);
        } catch (Exception e) {
            log.error("阿里云短信发送失败 phone={}", phone, e);
        }
    }
}
