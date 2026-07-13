package org.icedAmericanoMall.integration.payment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付宝配置（预留给将来真实接入；当前走 Mock）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {
    /** 是否启用真实支付宝客户端；false（默认）走 Mock。 */
    private boolean enabled = false;
    private String appId;
    private String privateKey;
    private String alipayPublicKey;
    private String gatewayUrl = "https://openapi.alipay.com/gateway.do";
    private String notifyUrl;
}
