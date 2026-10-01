package org.icedamericanomall.integration.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
/**
 * @ClassName: WechatConfigProperties
 * @Description: 向微信服务接口请求
 * @Author: noLazy
 * @Date: 2026/9/9 21:55
 * @Version: 1.0.0
 * @ProjectName: back-end
 * @Package: org.icedamericanomall.integration.auth.config
 */

@Data
@ConfigurationProperties(prefix="wechat.mini")
public class WechatMiniProperties {
    // 小程序ID
    private String appId;

    // 小程序密钥
    private String appSecret;
}
