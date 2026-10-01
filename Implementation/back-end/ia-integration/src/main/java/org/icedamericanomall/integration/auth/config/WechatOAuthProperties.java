package org.icedamericanomall.integration.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信 OAuth 配置。留空 app-id 时走 Mock 客户端（返回虚拟 openid）。
 */
@Data
@ConfigurationProperties(prefix = "wechat.oauth")
public class WechatOAuthProperties {
    /** 是否启用真实微信客户端；false（默认）走 Mock。 */
    private boolean enabled = false;
    private String appId;
    private String appSecret;
}
