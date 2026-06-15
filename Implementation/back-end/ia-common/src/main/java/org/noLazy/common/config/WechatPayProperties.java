package org.noLazy.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信支付 API v3 配置属性。
 *
 * 配置示例 (application.yml):
 * <pre>
 * wechat:
 *   pay:
 *     merchant-id: 1234567890
 *     private-key-path: /path/to/apiclient_key.pem
 *     merchant-serial-number: ABCDEFG1234567
 *     api-v3-key: your-api-v3-key
 *     notify-url: https://your-domain.com/api/pay/callback/wechat
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "wechat.pay")
public class WechatPayProperties {

    /** 微信支付商户号 */
    private String merchantId;

    /** 商户 API 私钥文件路径 (PEM格式) */
    private String privateKeyPath;

    /** 商户 API 证书序列号 */
    private String merchantSerialNumber;

    /** API v3 密钥 (32位，用于回调通知签名验证) */
    private String apiV3Key;

    /** 支付结果通知地址（公网可访问） */
    private String notifyUrl;
}
