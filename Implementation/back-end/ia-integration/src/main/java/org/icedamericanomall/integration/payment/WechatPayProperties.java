package org.icedamericanomall.integration.payment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wechat.pay")
public class WechatPayProperties {
    private String merchantId;
    private String privateKeyPath;
    private String merchantSerialNumber;
    private String apiV3Key;
    private String notifyUrl;
}
